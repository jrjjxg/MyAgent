package com.xg.platform.api.model;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.config.PlatformProperties;
import com.xg.platform.api.persistence.mybatisplus.entity.UserModelProviderConfigEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.UserModelProviderConfigMapper;
import com.xg.platform.api.skill.SkillSecretCrypto;
import com.xg.platform.contracts.model.ModelProviderStatusRecord;
import com.xg.platform.contracts.model.ModelProviderStatusResponse;
import com.xg.platform.contracts.model.UpdateModelProviderConfigRequest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;
import io.micrometer.observation.ObservationRegistry;
import com.google.genai.Client;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

public class ModelProviderConfigService {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final List<String> SUPPORTED_PROVIDER_IDS = List.of("gemini", "openai", "deepseek");
    private static final Map<String, String> PROVIDER_DISPLAY_NAMES = Map.of(
            "gemini", "Gemini",
            "openai", "OpenAI",
            "deepseek", "DeepSeek"
    );

    private final UserModelProviderConfigMapper mapper;
    private final SkillSecretCrypto skillSecretCrypto;
    private final PlatformProperties properties;

    public ModelProviderConfigService(UserModelProviderConfigMapper mapper,
                                      SkillSecretCrypto skillSecretCrypto,
                                      PlatformProperties properties) {
        this.mapper = mapper;
        this.skillSecretCrypto = skillSecretCrypto;
        this.properties = properties;
    }

    public boolean secretStorageAvailable() {
        return skillSecretCrypto != null;
    }

    public ModelProviderStatusResponse listStatus(String userId) {
        List<ModelProviderStatusRecord> providers = SUPPORTED_PROVIDER_IDS.stream()
                .map(providerId -> toStatusRecord(resolveEffectiveConfig(userId, providerId)))
                .toList();
        return new ModelProviderStatusResponse(
                secretStorageAvailable(),
                normalizeRequestedProviderId(properties.getModel().getDefaultProvider()),
                providers
        );
    }

    public ModelProviderStatusRecord updateProviderConfig(String userId,
                                                          String providerId,
                                                          UpdateModelProviderConfigRequest request) {
        String normalizedProviderId = normalizeRequestedProviderId(providerId);
        if (request != null && request.apiKey() != null && !request.apiKey().isBlank() && !secretStorageAvailable()) {
            throw new IllegalStateException("Model API key storage is not enabled");
        }
        UserModelProviderConfigEntity existing = findEntity(userId, normalizedProviderId);
        boolean enabled = request != null && request.enabled() != null
                ? request.enabled()
                : existing != null && existing.getEnabled() != null
                ? existing.getEnabled()
                : systemProvider(normalizedProviderId).enabled();

        String apiKey = request != null && request.apiKey() != null
                ? trimToNull(request.apiKey())
                : decryptApiKey(existing);
        String modelOverride = request != null && request.model() != null
                ? trimToNull(request.model())
                : existing == null
                ? null
                : trimToNull(existing.getModelOverride());
        String baseUrlOverride = request != null && request.baseUrl() != null
                ? trimToNull(request.baseUrl())
                : existing == null
                ? null
                : trimToNull(existing.getBaseUrlOverride());

        UserModelProviderConfigEntity entity = existing == null ? new UserModelProviderConfigEntity() : existing;
        Instant now = Instant.now();
        entity.setConfigId(existing == null ? UUID.randomUUID().toString() : existing.getConfigId());
        entity.setUserId(userId);
        entity.setProviderId(normalizedProviderId);
        entity.setEnabled(enabled);
        if (apiKey == null) {
            entity.setApiKeyCiphertext(null);
            entity.setApiKeyIv(null);
        } else {
            SkillSecretCrypto.EncryptedPayload encryptedPayload = encryptApiKey(apiKey);
            entity.setApiKeyCiphertext(encryptedPayload.ciphertext());
            entity.setApiKeyIv(encryptedPayload.iv());
        }
        entity.setModelOverride(modelOverride);
        entity.setBaseUrlOverride(baseUrlOverride);
        entity.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        entity.setUpdatedAt(now);
        if (existing == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return toStatusRecord(resolveEffectiveConfig(userId, normalizedProviderId));
    }

    public ResolvedModelProvider resolveProvider(String userId,
                                                 String requestedProviderId,
                                                 ToolCallingManager toolCallingManager) {
        EffectiveConfig config = resolveEffectiveConfig(userId, requestedProviderId);
        validateProviderAvailability(config);
        ChatModel chatModel = buildChatModel(config, toolCallingManager);
        return new ResolvedModelProvider(config.providerId(), config.model(), chatModel);
    }

    private void validateProviderAvailability(EffectiveConfig config) {
        if (!config.enabled()) {
            throw new IllegalArgumentException("Provider is disabled: " + config.providerId());
        }
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalArgumentException("Provider " + config.providerId() + " is not configured: missing API key");
        }
        if (config.model() == null || config.model().isBlank()) {
            throw new IllegalArgumentException("Provider " + config.providerId() + " is not configured: missing model");
        }
    }

    private ChatModel buildChatModel(EffectiveConfig config, ToolCallingManager toolCallingManager) {
        return switch (config.providerId()) {
            case "gemini" -> GoogleGenAiChatModel.builder()
                    .genAiClient(Client.builder().apiKey(config.apiKey()).build())
                    .defaultOptions(GoogleGenAiChatOptions.builder().model(config.model()).build())
                    .toolCallingManager(toolCallingManager)
                    .retryTemplate(RetryTemplate.defaultInstance())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .build();
            case "openai" -> OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl(resolveRequired(config.baseUrl(), OPENAI_BASE_URL))
                            .apiKey(config.apiKey())
                            .build())
                    .defaultOptions(OpenAiChatOptions.builder().model(config.model()).build())
                    .toolCallingManager(toolCallingManager)
                    .retryTemplate(RetryTemplate.defaultInstance())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .build();
            case "deepseek" -> {
                String baseUrl = normalizeDeepSeekBaseUrl(config.baseUrl());
                yield DeepSeekChatModel.builder()
                        .deepSeekApi(DeepSeekApi.builder()
                                .baseUrl(baseUrl)
                                .apiKey(config.apiKey())
                                .build())
                        .defaultOptions(DeepSeekChatOptions.builder().model(config.model()).build())
                        .toolCallingManager(toolCallingManager)
                        .retryTemplate(RetryTemplate.defaultInstance())
                        .observationRegistry(ObservationRegistry.NOOP)
                        .build();
            }
            default -> throw new NoSuchElementException("Unsupported provider: " + config.providerId());
        };
    }

    private ModelProviderStatusRecord toStatusRecord(EffectiveConfig config) {
        return new ModelProviderStatusRecord(
                config.providerId(),
                PROVIDER_DISPLAY_NAMES.getOrDefault(config.providerId(), config.providerId()),
                config.enabled(),
                config.ready(),
                config.apiKeyConfigured(),
                config.customApiKeyConfigured(),
                config.systemApiKeyConfigured(),
                blankFallback(config.model(), ""),
                blankFallback(config.customModel(), ""),
                blankFallback(config.baseUrl(), ""),
                blankFallback(config.customBaseUrl(), ""),
                config.supportsCustomBaseUrl()
        );
    }

    private EffectiveConfig resolveEffectiveConfig(String userId, String requestedProviderId) {
        String providerId = normalizeRequestedProviderId(requestedProviderId);
        SystemProviderConfig systemProvider = systemProvider(providerId);
        UserModelProviderConfigEntity entity = findEntity(userId, providerId);
        String customApiKey = decryptApiKey(entity);
        String customModel = entity == null ? null : trimToNull(entity.getModelOverride());
        String customBaseUrl = entity == null ? null : trimToNull(entity.getBaseUrlOverride());
        boolean enabled = entity != null && entity.getEnabled() != null ? entity.getEnabled() : systemProvider.enabled();
        String effectiveApiKey = firstNonBlank(customApiKey, systemProvider.apiKey());
        String effectiveModel = firstNonBlank(customModel, systemProvider.model());
        String effectiveBaseUrl = firstNonBlank(customBaseUrl, systemProvider.baseUrl());
        boolean apiKeyConfigured = effectiveApiKey != null && !effectiveApiKey.isBlank();
        return new EffectiveConfig(
                providerId,
                enabled,
                effectiveApiKey,
                effectiveModel,
                effectiveBaseUrl,
                customApiKey != null && !customApiKey.isBlank(),
                systemProvider.apiKey() != null && !systemProvider.apiKey().isBlank(),
                customModel,
                customBaseUrl,
                supportsCustomBaseUrl(providerId),
                apiKeyConfigured && effectiveModel != null && !effectiveModel.isBlank()
        );
    }

    private UserModelProviderConfigEntity findEntity(String userId, String providerId) {
        return mapper.selectOne(
                Wrappers.<UserModelProviderConfigEntity>lambdaQuery()
                        .eq(UserModelProviderConfigEntity::getUserId, userId)
                        .eq(UserModelProviderConfigEntity::getProviderId, providerId)
                        .last("limit 1")
        );
    }

    private SkillSecretCrypto.EncryptedPayload encryptApiKey(String apiKey) {
        if (!secretStorageAvailable()) {
            throw new IllegalStateException("Model API key storage is not enabled");
        }
        return skillSecretCrypto.encrypt(apiKey.getBytes(StandardCharsets.UTF_8));
    }

    private String decryptApiKey(UserModelProviderConfigEntity entity) {
        if (entity == null || entity.getApiKeyCiphertext() == null || entity.getApiKeyCiphertext().length == 0) {
            return null;
        }
        if (!secretStorageAvailable()) {
            return null;
        }
        return new String(skillSecretCrypto.decrypt(entity.getApiKeyCiphertext(), entity.getApiKeyIv()), StandardCharsets.UTF_8).trim();
    }

    private String normalizeRequestedProviderId(String providerId) {
        String candidate = providerId == null || providerId.isBlank()
                ? properties.getModel().getDefaultProvider()
                : providerId.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROVIDER_IDS.contains(candidate)) {
            throw new IllegalArgumentException("Unsupported provider: " + providerId);
        }
        return candidate;
    }

    private SystemProviderConfig systemProvider(String providerId) {
        PlatformProperties.Provider provider = switch (providerId) {
            case "gemini" -> properties.getModel().getProviders().getGemini();
            case "openai" -> properties.getModel().getProviders().getOpenai();
            case "deepseek" -> properties.getModel().getProviders().getDeepseek();
            default -> throw new IllegalArgumentException("Unsupported provider: " + providerId);
        };
        return switch (providerId) {
            case "gemini" -> new SystemProviderConfig(
                    provider.isEnabled(),
                    resolveRequired(provider.getApiKey(), properties.getAi().getGemini().getApiKey()),
                    resolveRequired(provider.getModel(), properties.getAi().getGemini().getModel()),
                    resolveRequired(provider.getBaseUrl(), properties.getAi().getGemini().getBaseUrl())
            );
            case "openai" -> new SystemProviderConfig(
                    provider.isEnabled(),
                    provider.getApiKey(),
                    provider.getModel(),
                    resolveRequired(provider.getBaseUrl(), OPENAI_BASE_URL)
            );
            case "deepseek" -> new SystemProviderConfig(
                    provider.isEnabled(),
                    provider.getApiKey(),
                    provider.getModel(),
                    normalizeDeepSeekBaseUrl(resolveRequired(provider.getBaseUrl(), DEEPSEEK_BASE_URL))
            );
            default -> throw new IllegalArgumentException("Unsupported provider: " + providerId);
        };
    }

    private boolean supportsCustomBaseUrl(String providerId) {
        return "openai".equals(providerId) || "deepseek".equals(providerId);
    }

    private String resolveRequired(String configuredValue, String fallback) {
        return configuredValue != null && !configuredValue.isBlank() ? configuredValue : fallback;
    }

    private String normalizeDeepSeekBaseUrl(String baseUrl) {
        String normalized = resolveRequired(baseUrl, DEEPSEEK_BASE_URL);
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String blankFallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private record SystemProviderConfig(
            boolean enabled,
            String apiKey,
            String model,
            String baseUrl
    ) {
    }

    private record EffectiveConfig(
            String providerId,
            boolean enabled,
            String apiKey,
            String model,
            String baseUrl,
            boolean customApiKeyConfigured,
            boolean systemApiKeyConfigured,
            String customModel,
            String customBaseUrl,
            boolean supportsCustomBaseUrl,
            boolean ready
    ) {
        private boolean apiKeyConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record ResolvedModelProvider(
            String providerId,
            String model,
            ChatModel chatModel
    ) {
    }
}
