package com.xg.platform.agent.core;

import com.xg.platform.contracts.agent.AgentCapability;
import com.xg.platform.agent.core.chat.ChatRouteKind;

import java.util.List;

public record AgentExecutionResult(
        String agentId,
        String providerId,
        AgentCapability capability,
        String summary,
        String finalContent,
        boolean persistFinalArtifact,
        String workflow,
        ChatRouteKind routeKind,
        boolean toolsEnabled,
        List<ExecutionSource> sources,
        int usedVerifiedSources
) {
    public AgentExecutionResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
        usedVerifiedSources = Math.max(0, usedVerifiedSources);
    }

    public AgentExecutionResult(String agentId,
                                String providerId,
                                AgentCapability capability,
                                String summary,
                                String finalContent,
                                boolean persistFinalArtifact) {
        this(
                agentId,
                providerId,
                capability,
                summary,
                finalContent,
                persistFinalArtifact,
                null,
                null,
                false,
                List.of(),
                0
        );
    }
}
