package com.xg.platform.api.search;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.config.PlatformProperties;
import com.xg.platform.api.persistence.mybatisplus.entity.UserWebSearchConfigEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.UserWebSearchConfigMapper;
import com.xg.platform.api.skill.SkillSecretCrypto;
import com.xg.platform.contracts.tooling.UpdateWebSearchSettingsRequest;
import com.xg.platform.contracts.tooling.WebSearchSettingsRecord;
import com.xg.platform.contracts.tooling.WebSearchSettingsResponse;
import com.xg.platform.tooling.port.WebSearchSettingsResolver;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class WebSearchConfigService implements WebSearchSettingsResolver {

    private static final List<String> SUPPORTED_PROVIDERS = List.of("auto", "tavily", "duckduckgo");

    private final UserWebSearchConfigMapper mapper;
    private final SkillSecretCrypto skillSecretCrypto;
    private final PlatformProperties properties;

    public WebSearchConfigService(UserWebSearchConfigMapper mapper,
                                  SkillSecretCrypto skillSecretCrypto,
                                  PlatformProperties properties) {
        this.mapper = mapper;
        this.skillSecretCrypto = skillSecretCrypto;
        this.properties = properties;
    }

    public boolean secretStorageAvailable() {
        return skillSecretCrypto != null;
    }

    public WebSearchSettingsResponse getSettings(String userId) {
        return new WebSearchSettingsResponse(secretStorageAvailable(), toRecord(resolveEffectiveConfig(userId)));
    }

    public WebSearchSettingsRecord updateSettings(String userId, UpdateWebSearchSettingsRequest request) {
        if (request != null
                && request.tavilyApiKey() != null
                && !request.tavilyApiKey().isBlank()
                && !secretStorageAvailable()) {
            throw new IllegalStateException("Web search API key storage is not enabled");
        }
        UserWebSearchConfigEntity existing = findEntity(userId);
        String providerOverride = request != null && request.provider() != null
                ? normalizeProvider(request.provider())
                : existing == null
                ? null
                : trimToNull(existing.getProviderOverride());
        String tavilyApiKey = request != null && request.tavilyApiKey() != null
                ? trimToNull(request.tavilyApiKey())
                : decryptTavilyApiKey(existing);
        String searchApiBaseUrlOverride = request != null && request.searchApiBaseUrl() != null
                ? trimToNull(request.searchApiBaseUrl())
                : existing == null
                ? null
                : trimToNull(existing.getSearchApiBaseUrlOverride());

        UserWebSearchConfigEntity entity = existing == null ? new UserWebSearchConfigEntity() : existing;
        Instant now = Instant.now();
        entity.setConfigId(existing == null ? UUID.randomUUID().toString() : existing.getConfigId());
        entity.setUserId(userId);
        entity.setProviderOverride(providerOverride);
        if (tavilyApiKey == null) {
            entity.setTavilyApiKeyCiphertext(null);
            entity.setTavilyApiKeyIv(null);
        } else {
            SkillSecretCrypto.EncryptedPayload encryptedPayload = encryptSecret(tavilyApiKey);
            entity.setTavilyApiKeyCiphertext(encryptedPayload.ciphertext());
            entity.setTavilyApiKeyIv(encryptedPayload.iv());
        }
        entity.setSearchApiBaseUrlOverride(searchApiBaseUrlOverride);
        entity.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        entity.setUpdatedAt(now);
        if (existing == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return toRecord(resolveEffectiveConfig(userId));
    }

    @Override
    public WebSearchSettingsResolver.ResolvedWebSearchSettings resolve(String userId) {
        EffectiveConfig config = resolveEffectiveConfig(userId);
        return new WebSearchSettingsResolver.ResolvedWebSearchSettings(
                config.effectiveProvider(),
                config.effectiveSearchApiBaseUrl(),
                config.effectiveTavilyApiKey()
        );
    }

    private WebSearchSettingsRecord toRecord(EffectiveConfig config) {
        return new WebSearchSettingsRecord(
                config.effectiveProvider(),
                blankFallback(config.customProvider(), ""),
                config.tavilyApiKeyConfigured(),
                config.customTavilyApiKeyConfigured(),
                config.systemTavilyApiKeyConfigured(),
                blankFallback(config.effectiveSearchApiBaseUrl(), ""),
                blankFallback(config.customSearchApiBaseUrl(), "")
        );
    }

    private EffectiveConfig resolveEffectiveConfig(String userId) {
        UserWebSearchConfigEntity entity = findEntity(userId);
        PlatformProperties.Web systemWeb = properties.getTools().getWeb();
        String customProvider = entity == null ? null : trimToNull(entity.getProviderOverride());
        String customTavilyApiKey = decryptTavilyApiKey(entity);
        String customSearchApiBaseUrl = entity == null ? null : trimToNull(entity.getSearchApiBaseUrlOverride());
        String systemProvider = normalizeProvider(systemWeb.getProvider());
        String systemTavilyApiKey = trimToNull(systemWeb.getTavilyApiKey());
        String systemSearchApiBaseUrl = trimToNull(systemWeb.getSearchApiBaseUrl());
        return new EffectiveConfig(
                firstNonBlank(customProvider, systemProvider),
                customProvider,
                firstNonBlank(customTavilyApiKey, systemTavilyApiKey),
                customTavilyApiKey != null && !customTavilyApiKey.isBlank(),
                systemTavilyApiKey != null && !systemTavilyApiKey.isBlank(),
                firstNonBlank(customSearchApiBaseUrl, systemSearchApiBaseUrl),
                customSearchApiBaseUrl
        );
    }

    private UserWebSearchConfigEntity findEntity(String userId) {
        return mapper.selectOne(
                Wrappers.<UserWebSearchConfigEntity>lambdaQuery()
                        .eq(UserWebSearchConfigEntity::getUserId, userId)
                        .last("limit 1")
        );
    }

    private SkillSecretCrypto.EncryptedPayload encryptSecret(String value) {
        if (!secretStorageAvailable()) {
            throw new IllegalStateException("Web search API key storage is not enabled");
        }
        return skillSecretCrypto.encrypt(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decryptTavilyApiKey(UserWebSearchConfigEntity entity) {
        if (entity == null || entity.getTavilyApiKeyCiphertext() == null || entity.getTavilyApiKeyCiphertext().length == 0) {
            return null;
        }
        if (!secretStorageAvailable()) {
            return null;
        }
        return new String(
                skillSecretCrypto.decrypt(entity.getTavilyApiKeyCiphertext(), entity.getTavilyApiKeyIv()),
                StandardCharsets.UTF_8
        ).trim();
    }

    private String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported web search provider: " + value);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String blankFallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private record EffectiveConfig(
            String effectiveProvider,
            String customProvider,
            String effectiveTavilyApiKey,
            boolean customTavilyApiKeyConfigured,
            boolean systemTavilyApiKeyConfigured,
            String effectiveSearchApiBaseUrl,
            String customSearchApiBaseUrl
    ) {
        private boolean tavilyApiKeyConfigured() {
            return effectiveTavilyApiKey != null && !effectiveTavilyApiKey.isBlank();
        }
    }
}
