package com.xg.platform.tools;

import java.time.Instant;
import java.util.Map;

public record SkillUserConfig(
        String userId,
        String skillId,
        boolean enabled,
        Map<String, String> env,
        Instant createdAt,
        Instant updatedAt
) {
    public SkillUserConfig {
        env = env == null ? Map.of() : Map.copyOf(env);
    }
}
