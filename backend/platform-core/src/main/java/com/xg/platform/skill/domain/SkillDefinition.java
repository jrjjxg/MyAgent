package com.xg.platform.skill.domain;

import java.nio.file.Path;
import java.util.List;

public record SkillDefinition(
        String skillId,
        String sourceKey,
        String description,
        String summary,
        String homepage,
        String primaryEnv,
        List<String> requiredEnvs,
        List<String> triggers,
        List<String> preferredTools,
        List<String> allowedTools,
        List<String> resources,
        List<String> mcpServers,
        List<SkillPackageCommand> packageCommands,
        boolean requiresDocuments,
        boolean requiresWeb,
        String agent,
        SkillInvocation invocation,
        SkillExecutionMode execution,
        Path sourcePath,
        String body,
        boolean enabled,
        String source,
        SkillAvailabilityStatus availabilityStatus,
        String availabilityReason
) {

    public SkillDefinition {
        homepage = homepage == null ? "" : homepage;
        primaryEnv = primaryEnv == null ? "" : primaryEnv.trim();
        requiredEnvs = requiredEnvs == null ? List.of() : List.copyOf(requiredEnvs);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        preferredTools = preferredTools == null ? List.of() : List.copyOf(preferredTools);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        resources = resources == null ? List.of() : List.copyOf(resources);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        packageCommands = packageCommands == null ? List.of() : List.copyOf(packageCommands);
        summary = summary == null ? "" : summary;
        agent = agent == null || agent.isBlank() ? "general-agent" : agent;
        invocation = invocation == null ? SkillInvocation.AUTO : invocation;
        execution = execution == null ? SkillExecutionMode.INLINE : execution;
        body = body == null ? "" : body;
        source = source == null || source.isBlank() ? "public" : source;
        sourceKey = sourceKey == null || sourceKey.isBlank()
                ? source + ":" + skillId
                : sourceKey;
        availabilityStatus = availabilityStatus == null ? SkillAvailabilityStatus.READY : availabilityStatus;
        availabilityReason = availabilityReason == null ? "" : availabilityReason.trim();
    }

    public SkillDefinition withEnabled(boolean enabled) {
        return new SkillDefinition(
                skillId,
                sourceKey,
                description,
                summary,
                homepage,
                primaryEnv,
                requiredEnvs,
                triggers,
                preferredTools,
                allowedTools,
                resources,
                mcpServers,
                packageCommands,
                requiresDocuments,
                requiresWeb,
                agent,
                invocation,
                execution,
                sourcePath,
                body,
                enabled,
                source,
                availabilityStatus,
                availabilityReason
        );
    }

    public SkillDefinition withBody(String body) {
        return new SkillDefinition(
                skillId,
                sourceKey,
                description,
                summary,
                homepage,
                primaryEnv,
                requiredEnvs,
                triggers,
                preferredTools,
                allowedTools,
                resources,
                mcpServers,
                packageCommands,
                requiresDocuments,
                requiresWeb,
                agent,
                invocation,
                execution,
                sourcePath,
                body,
                enabled,
                source,
                availabilityStatus,
                availabilityReason
        );
    }

    public SkillDefinition withAvailability(SkillAvailabilityStatus availabilityStatus, String availabilityReason, boolean enabled) {
        return new SkillDefinition(
                skillId,
                sourceKey,
                description,
                summary,
                homepage,
                primaryEnv,
                requiredEnvs,
                triggers,
                preferredTools,
                allowedTools,
                resources,
                mcpServers,
                packageCommands,
                requiresDocuments,
                requiresWeb,
                agent,
                invocation,
                execution,
                sourcePath,
                body,
                enabled,
                source,
                availabilityStatus,
                availabilityReason
        );
    }
}
