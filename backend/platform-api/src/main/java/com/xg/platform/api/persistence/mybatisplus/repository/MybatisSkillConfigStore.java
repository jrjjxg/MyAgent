package com.xg.platform.api.persistence.mybatisplus.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.SkillUserConfigEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.SkillUserConfigMapper;
import com.xg.platform.api.skill.SkillSecretCrypto;
import com.xg.platform.skill.port.SkillConfigStore;
import com.xg.platform.skill.domain.SkillUserConfig;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MybatisSkillConfigStore implements SkillConfigStore {

    private static final TypeReference<Map<String, String>> ENV_TYPE = new TypeReference<>() {
    };

    private final SkillUserConfigMapper mapper;
    private final ObjectMapper objectMapper;
    private final SkillSecretCrypto skillSecretCrypto;

    public MybatisSkillConfigStore(SkillUserConfigMapper mapper,
                                   ObjectMapper objectMapper,
                                   SkillSecretCrypto skillSecretCrypto) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.skillSecretCrypto = skillSecretCrypto;
    }

    @Override
    public Optional<SkillUserConfig> find(String userId, String skillId) {
        SkillUserConfigEntity entity = mapper.selectOne(
                Wrappers.<SkillUserConfigEntity>lambdaQuery()
                        .eq(SkillUserConfigEntity::getUserId, userId)
                        .eq(SkillUserConfigEntity::getSkillId, skillId)
                        .last("limit 1")
        );
        return Optional.ofNullable(entity).map(this::toRecord);
    }

    @Override
    public List<SkillUserConfig> list(String userId) {
        return mapper.selectList(
                        Wrappers.<SkillUserConfigEntity>lambdaQuery()
                                .eq(SkillUserConfigEntity::getUserId, userId)
                                .orderByAsc(SkillUserConfigEntity::getSkillId))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public SkillUserConfig save(String userId, String skillId, boolean enabled, Map<String, String> env) {
        if (!secretStorageAvailable()) {
            throw new IllegalStateException("Skill secret storage is not enabled");
        }
        SkillUserConfigEntity existing = mapper.selectOne(
                Wrappers.<SkillUserConfigEntity>lambdaQuery()
                        .eq(SkillUserConfigEntity::getUserId, userId)
                        .eq(SkillUserConfigEntity::getSkillId, skillId)
                        .last("limit 1")
        );
        Instant now = Instant.now();
        Map<String, String> normalizedEnv = normalizeEnv(env);
        SkillSecretCrypto.EncryptedPayload encryptedPayload = encrypt(normalizedEnv);
        SkillUserConfigEntity entity = existing == null ? new SkillUserConfigEntity() : existing;
        entity.setConfigId(existing == null ? UUID.randomUUID().toString() : existing.getConfigId());
        entity.setUserId(userId);
        entity.setSkillId(skillId);
        entity.setEnabled(enabled);
        entity.setEnvCiphertext(encryptedPayload.ciphertext());
        entity.setEnvIv(encryptedPayload.iv());
        entity.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        entity.setUpdatedAt(now);
        if (existing == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return toRecord(entity);
    }

    @Override
    public boolean secretStorageAvailable() {
        return skillSecretCrypto != null;
    }

    private SkillUserConfig toRecord(SkillUserConfigEntity entity) {
        return new SkillUserConfig(
                entity.getUserId(),
                entity.getSkillId(),
                Boolean.TRUE.equals(entity.getEnabled()),
                decrypt(entity),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Map<String, String> decrypt(SkillUserConfigEntity entity) {
        if (!secretStorageAvailable()) {
            return Map.of();
        }
        byte[] ciphertext = entity.getEnvCiphertext();
        byte[] iv = entity.getEnvIv();
        if (ciphertext == null || ciphertext.length == 0 || iv == null || iv.length == 0) {
            return Map.of();
        }
        try {
            byte[] plaintext = skillSecretCrypto.decrypt(ciphertext, iv);
            return normalizeEnv(objectMapper.readValue(plaintext, ENV_TYPE));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decode stored skill config for " + entity.getSkillId(), exception);
        }
    }

    private SkillSecretCrypto.EncryptedPayload encrypt(Map<String, String> env) {
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(env);
            return skillSecretCrypto.encrypt(plaintext);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encode skill config", exception);
        }
    }

    private Map<String, String> normalizeEnv(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        env.forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.trim();
            String normalizedValue = value == null ? "" : value.trim();
            if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                normalized.put(normalizedKey, normalizedValue);
            }
        });
        return Map.copyOf(normalized);
    }
}
