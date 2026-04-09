package com.xg.platform.contracts.skill;

import java.io.Serializable;

public record SkillDescriptor(
        String skillId,
        String sourceKey,
        String description,
        String summary,
        String homepage,
        String primaryEnv,
        java.util.List<String> requiredEnvs,
        java.util.List<String> triggers,
        java.util.List<String> preferredTools,
        java.util.List<String> allowedTools,
        java.util.List<String> resources,
        java.util.List<String> mcpServers,
        java.util.List<String> packageCommands,
        boolean requiresDocuments,
        boolean requiresWeb,
        String agent,
        String invocation,
        String execution,
        boolean enabled,
        String source,
        String path,
        String status,
        String statusReason
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
