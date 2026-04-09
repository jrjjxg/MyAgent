package com.xg.platform.api.skill;

import com.xg.platform.contracts.skill.SkillStatusRecord;
import com.xg.platform.contracts.skill.SkillStatusResponse;
import com.xg.platform.contracts.skill.UpdateSkillConfigRequest;
import com.xg.platform.tools.SkillConfigStore;
import com.xg.platform.tools.SkillDefinition;
import com.xg.platform.tools.SkillRegistry;
import com.xg.platform.tools.SkillUserConfig;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SkillConfigService {

    private final SkillRegistry skillRegistry;
    private final SkillConfigStore skillConfigStore;
    private final Environment environment;

    public SkillConfigService(SkillRegistry skillRegistry,
                              SkillConfigStore skillConfigStore,
                              Environment environment) {
        this.skillRegistry = skillRegistry;
        this.skillConfigStore = skillConfigStore;
        this.environment = environment;
    }

    public boolean secretStorageAvailable() {
        return skillConfigStore.secretStorageAvailable();
    }

    public SkillStatusResponse listStatus(String userId) {
        List<SkillDefinition> discoveredSkills = skillRegistry.listDiscoveredSkills();
        Map<String, SkillUserConfig> configsBySkillId = new LinkedHashMap<>();
        for (SkillUserConfig config : skillConfigStore.list(userId)) {
            configsBySkillId.put(config.skillId(), config);
        }
        Set<String> enabledSkillIds = skillRegistry.snapshotForUser(userId).skills().stream()
                .filter(SkillDefinition::enabled)
                .map(SkillDefinition::skillId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<SkillStatusRecord> skills = discoveredSkills.stream()
                .map(skill -> toStatusRecord(skill, configsBySkillId.get(skill.skillId()), enabledSkillIds.contains(skill.skillId())))
                .toList();
        return new SkillStatusResponse(secretStorageAvailable(), skills);
    }

    public SkillStatusRecord updateSkillConfig(String userId,
                                               String skillId,
                                               UpdateSkillConfigRequest request) {
        SkillDefinition skill = skillRegistry.requireSkill(userId, skillId);
        SkillUserConfig existing = skillConfigStore.find(userId, skill.skillId()).orElse(null);
        boolean enabled = request != null && request.enabled() != null
                ? request.enabled()
                : existing == null || existing.enabled();
        Map<String, String> mergedEnv = new LinkedHashMap<>(existing == null ? Map.of() : existing.env());
        Set<String> declaredEnvKeys = declaredEnvKeys(skill);
        if (request != null && request.apiKey() != null && !skill.primaryEnv().isBlank()) {
            updateEnvValue(mergedEnv, skill.primaryEnv(), request.apiKey());
        }
        if (request != null && request.env() != null) {
            for (Map.Entry<String, String> entry : request.env().entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                if (key.isBlank() || !declaredEnvKeys.contains(key)) {
                    throw new IllegalArgumentException("Unsupported env key for skill " + skill.skillId() + ": " + key);
                }
                updateEnvValue(mergedEnv, key, entry.getValue());
            }
        }
        skillConfigStore.save(userId, skill.skillId(), enabled, mergedEnv);
        return listStatus(userId).skills().stream()
                .filter(status -> status.skillId().equals(skill.skillId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Updated skill status not found: " + skill.skillId()));
    }

    public Map<String, String> resolveRuntimeEnv(String userId, List<String> activeSkillIds) {
        if (userId == null || userId.isBlank() || activeSkillIds == null || activeSkillIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> env = new LinkedHashMap<>();
        for (String activeSkillId : activeSkillIds) {
            if (activeSkillId == null || activeSkillId.isBlank()) {
                continue;
            }
            SkillDefinition skill = skillRegistry.requireSkill(userId, activeSkillId);
            SkillUserConfig config = skillConfigStore.find(userId, skill.skillId()).orElse(null);
            if (config == null || config.env().isEmpty()) {
                continue;
            }
            for (String key : declaredEnvKeys(skill)) {
                String value = config.env().get(key);
                if (value != null && !value.isBlank()) {
                    env.put(key, value);
                }
            }
        }
        return Map.copyOf(env);
    }

    private SkillStatusRecord toStatusRecord(SkillDefinition skill,
                                             SkillUserConfig config,
                                             boolean enabled) {
        List<String> declaredEnvKeys = new ArrayList<>(declaredEnvKeys(skill));
        List<String> configuredEnvKeys = declaredEnvKeys.stream()
                .filter(key -> isConfigured(key, config))
                .toList();
        List<String> missingEnvs = declaredEnvKeys.stream()
                .filter(key -> !configuredEnvKeys.contains(key))
                .toList();
        return new SkillStatusRecord(
                skill.skillId(),
                skill.sourceKey(),
                skill.description(),
                skill.summary(),
                skill.source(),
                skill.sourcePath().toString(),
                skill.homepage(),
                enabled,
                skill.primaryEnv(),
                skill.requiredEnvs(),
                skill.requiresDocuments(),
                skill.requiresWeb(),
                missingEnvs,
                configuredEnvKeys,
                enabled && missingEnvs.isEmpty()
        );
    }

    private Set<String> declaredEnvKeys(SkillDefinition skill) {
        Set<String> keys = new LinkedHashSet<>();
        if (skill != null) {
            if (skill.primaryEnv() != null && !skill.primaryEnv().isBlank()) {
                keys.add(skill.primaryEnv().trim());
            }
            if (skill.requiredEnvs() != null) {
                for (String env : skill.requiredEnvs()) {
                    if (env != null && !env.isBlank()) {
                        keys.add(env.trim());
                    }
                }
            }
        }
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }

    private boolean isConfigured(String key, SkillUserConfig config) {
        if (config != null) {
            String configured = config.env().get(key);
            if (configured != null && !configured.isBlank()) {
                return true;
            }
        }
        String systemValue = environment.getProperty(key);
        return systemValue != null && !systemValue.isBlank();
    }

    private void updateEnvValue(Map<String, String> env, String key, String value) {
        if (value == null || value.isBlank()) {
            env.remove(key);
            return;
        }
        env.put(key, value.trim());
    }
}
