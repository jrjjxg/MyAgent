package com.xg.platform.skill.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.xg.platform.skill.domain.SkillUserConfig;

public interface SkillConfigStore {

    Optional<SkillUserConfig> find(String userId, String skillId);

    List<SkillUserConfig> list(String userId);

    SkillUserConfig save(String userId, String skillId, boolean enabled, Map<String, String> env);

    boolean secretStorageAvailable();

    static SkillConfigStore disabled() {
        return new SkillConfigStore() {
            @Override
            public Optional<SkillUserConfig> find(String userId, String skillId) {
                return Optional.empty();
            }

            @Override
            public List<SkillUserConfig> list(String userId) {
                return List.of();
            }

            @Override
            public SkillUserConfig save(String userId, String skillId, boolean enabled, Map<String, String> env) {
                throw new IllegalStateException("Skill secret storage is not enabled");
            }

            @Override
            public boolean secretStorageAvailable() {
                return false;
            }
        };
    }
}
