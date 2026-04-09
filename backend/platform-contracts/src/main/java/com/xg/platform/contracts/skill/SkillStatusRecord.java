package com.xg.platform.contracts.skill;

import java.util.List;

public record SkillStatusRecord(
        String skillId,
        String sourceKey,
        String description,
        String summary,
        String source,
        String path,
        String homepage,
        boolean enabled,
        String primaryEnv,
        List<String> requiredEnvs,
        boolean requiresDocuments,
        boolean requiresWeb,
        List<String> missingEnvs,
        List<String> configuredEnvKeys,
        boolean ready
) {
}
