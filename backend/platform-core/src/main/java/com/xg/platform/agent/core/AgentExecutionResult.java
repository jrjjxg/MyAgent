package com.xg.platform.agent.core;

import com.xg.platform.contracts.shared.agent.AgentCapability;
import com.xg.platform.conversation.domain.ConversationRouteKind;

import java.util.List;

public record AgentExecutionResult(
        String agentId,
        String providerId,
        AgentCapability capability,
        String summary,
        String finalContent,
        boolean persistFinalArtifact,
        String workflow,
        ConversationRouteKind routeKind,
        boolean toolsEnabled,
        List<ExecutionSource> sources,
        int usedVerifiedSources
) {
    public AgentExecutionResult {
        sources = sources == null ? List.of() : List.copyOf(sources);
        usedVerifiedSources = Math.max(0, usedVerifiedSources);
    }

    public static AgentExecutionResult basic(String agentId,
                                             String providerId,
                                             AgentCapability capability,
                                             String summary,
                                             String finalContent,
                                             boolean persistFinalArtifact) {
        return new AgentExecutionResult(
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
