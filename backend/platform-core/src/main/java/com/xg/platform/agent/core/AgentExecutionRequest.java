package com.xg.platform.agent.core;

import com.xg.platform.contracts.agent.AgentCapability;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.ThreadFileReference;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import com.xg.platform.tools.SkillRuntimeSnapshot;

import java.util.List;

public record AgentExecutionRequest(
        String userId,
        String threadId,
        String runId,
        String message,
        String agentId,
        String providerId,
        List<AgentCapability> requestedCapabilities,
        List<String> skillIds,
        String skillSelectionMode,
        List<ArtifactRecord> artifacts,
        List<ThreadFileReference> uploadedFiles,
        List<ThreadFileReference> inputImages,
        List<MessageRecord> recentMessages,
        String sessionSummary,
        String longTermMemory,
        ChatRouteKind chatRouteKind,
        SkillRuntimeSnapshot skillRuntimeSnapshot,
        ToolUseLimits toolUseLimits,
        List<String> activeSkillIds,
        List<String> selectedDocumentIds
) {
    public AgentExecutionRequest {
        requestedCapabilities = requestedCapabilities == null ? List.of() : List.copyOf(requestedCapabilities);
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        uploadedFiles = uploadedFiles == null ? List.of() : List.copyOf(uploadedFiles);
        inputImages = inputImages == null ? List.of() : List.copyOf(inputImages);
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        activeSkillIds = activeSkillIds == null ? List.of() : List.copyOf(activeSkillIds);
        selectedDocumentIds = selectedDocumentIds == null ? List.of() : List.copyOf(selectedDocumentIds);
    }

    public AgentExecutionRequest(String userId,
                                 String threadId,
                                 String runId,
                                 String message,
                                 String agentId,
                                 String providerId,
                                 List<AgentCapability> requestedCapabilities,
                                 List<String> skillIds,
                                 String skillSelectionMode,
                                 List<ArtifactRecord> artifacts,
                                 List<ThreadFileReference> uploadedFiles,
                                 List<ThreadFileReference> inputImages,
                                 List<MessageRecord> recentMessages,
                                 String sessionSummary,
                                 String longTermMemory) {
        this(
                userId,
                threadId,
                runId,
                message,
                agentId,
                providerId,
                requestedCapabilities,
                skillIds,
                skillSelectionMode,
                artifacts,
                uploadedFiles,
                inputImages,
                recentMessages,
                sessionSummary,
                longTermMemory,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    public AgentExecutionRequest(String userId,
                                 String threadId,
                                 String runId,
                                 String message,
                                 String agentId,
                                 String providerId,
                                 List<AgentCapability> requestedCapabilities,
                                 List<String> skillIds,
                                 String skillSelectionMode,
                                 List<ArtifactRecord> artifacts,
                                 List<ThreadFileReference> uploadedFiles,
                                 List<ThreadFileReference> inputImages,
                                 List<MessageRecord> recentMessages,
                                 String sessionSummary,
                                 String longTermMemory,
                                 ChatRouteKind chatRouteKind) {
        this(
                userId,
                threadId,
                runId,
                message,
                agentId,
                providerId,
                requestedCapabilities,
                skillIds,
                skillSelectionMode,
                artifacts,
                uploadedFiles,
                inputImages,
                recentMessages,
                sessionSummary,
                longTermMemory,
                chatRouteKind,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    public AgentExecutionRequest(String userId,
                                 String threadId,
                                 String runId,
                                 String message,
                                 String agentId,
                                 String providerId,
                                 List<AgentCapability> requestedCapabilities,
                                 List<String> skillIds,
                                 String skillSelectionMode,
                                 List<ArtifactRecord> artifacts,
                                 List<ThreadFileReference> uploadedFiles,
                                 List<ThreadFileReference> inputImages,
                                 List<MessageRecord> recentMessages,
                                 String sessionSummary,
                                 String longTermMemory,
                                 ChatRouteKind chatRouteKind,
                                 SkillRuntimeSnapshot skillRuntimeSnapshot,
                                 ToolUseLimits toolUseLimits) {
        this(
                userId,
                threadId,
                runId,
                message,
                agentId,
                providerId,
                requestedCapabilities,
                skillIds,
                skillSelectionMode,
                artifacts,
                uploadedFiles,
                inputImages,
                recentMessages,
                sessionSummary,
                longTermMemory,
                chatRouteKind,
                skillRuntimeSnapshot,
                toolUseLimits,
                List.of(),
                List.of()
        );
    }

    public AgentExecutionRequest(String userId,
                                 String threadId,
                                 String runId,
                                 String message,
                                 String agentId,
                                 String providerId,
                                 List<AgentCapability> requestedCapabilities,
                                 List<String> skillIds,
                                 String skillSelectionMode,
                                 List<ArtifactRecord> artifacts,
                                 List<ThreadFileReference> uploadedFiles,
                                 List<MessageRecord> recentMessages,
                                 String sessionSummary,
                                 String longTermMemory) {
        this(
                userId,
                threadId,
                runId,
                message,
                agentId,
                providerId,
                requestedCapabilities,
                skillIds,
                skillSelectionMode,
                artifacts,
                uploadedFiles,
                List.of(),
                recentMessages,
                sessionSummary,
                longTermMemory
        );
    }

    public AgentExecutionRequest(String userId,
                                 String threadId,
                                 String runId,
                                 String message,
                                 String agentId,
                                 String providerId,
                                 List<AgentCapability> requestedCapabilities,
                                 List<String> skillIds,
                                 String skillSelectionMode,
                                 List<ArtifactRecord> artifacts,
                                 List<ThreadFileReference> uploadedFiles,
                                 List<MessageRecord> recentMessages,
                                 String sessionSummary,
                                 String longTermMemory,
                                 ChatRouteKind chatRouteKind) {
        this(
                userId,
                threadId,
                runId,
                message,
                agentId,
                providerId,
                requestedCapabilities,
                skillIds,
                skillSelectionMode,
                artifacts,
                uploadedFiles,
                List.of(),
                recentMessages,
                sessionSummary,
                longTermMemory,
                chatRouteKind
        );
    }

    public AgentExecutionRequest(String userId,
                                 String threadId,
                                 String runId,
                                 String message,
                                 String agentId,
                                 String providerId,
                                 List<AgentCapability> requestedCapabilities,
                                 List<String> skillIds,
                                 String skillSelectionMode,
                                 List<ArtifactRecord> artifacts,
                                 List<ThreadFileReference> uploadedFiles,
                                 List<MessageRecord> recentMessages,
                                 String sessionSummary,
                                 String longTermMemory,
                                 ChatRouteKind chatRouteKind,
                                 SkillRuntimeSnapshot skillRuntimeSnapshot,
                                 ToolUseLimits toolUseLimits) {
        this(
                userId,
                threadId,
                runId,
                message,
                agentId,
                providerId,
                requestedCapabilities,
                skillIds,
                skillSelectionMode,
                artifacts,
                uploadedFiles,
                List.of(),
                recentMessages,
                sessionSummary,
                longTermMemory,
                chatRouteKind,
                skillRuntimeSnapshot,
                toolUseLimits
        );
    }

    public AgentExecutionRequest(String userId,
                                 String threadId,
                                 String runId,
                                 String message,
                                 String agentId,
                                 String providerId,
                                 List<AgentCapability> requestedCapabilities,
                                 List<String> skillIds,
                                 String skillSelectionMode,
                                 List<ArtifactRecord> artifacts,
                                 List<ThreadFileReference> uploadedFiles,
                                 List<MessageRecord> recentMessages,
                                 String sessionSummary,
                                 String longTermMemory,
                                 ChatRouteKind chatRouteKind,
                                 SkillRuntimeSnapshot skillRuntimeSnapshot,
                                 ToolUseLimits toolUseLimits,
                                 List<String> activeSkillIds) {
        this(
                userId,
                threadId,
                runId,
                message,
                agentId,
                providerId,
                requestedCapabilities,
                skillIds,
                skillSelectionMode,
                artifacts,
                uploadedFiles,
                List.of(),
                recentMessages,
                sessionSummary,
                longTermMemory,
                chatRouteKind,
                skillRuntimeSnapshot,
                toolUseLimits,
                activeSkillIds,
                List.of()
        );
    }

    public AgentExecutionRequest(String userId,
                                 String threadId,
                                 String runId,
                                 String message,
                                 String agentId,
                                 String providerId,
                                 List<AgentCapability> requestedCapabilities,
                                 List<String> skillIds,
                                 String skillSelectionMode,
                                 List<ArtifactRecord> artifacts,
                                 List<ThreadFileReference> uploadedFiles,
                                 List<MessageRecord> recentMessages,
                                 String sessionSummary,
                                 String longTermMemory,
                                 ChatRouteKind chatRouteKind,
                                 SkillRuntimeSnapshot skillRuntimeSnapshot,
                                 ToolUseLimits toolUseLimits,
                                 List<String> activeSkillIds,
                                 List<String> selectedDocumentIds) {
        this(
                userId,
                threadId,
                runId,
                message,
                agentId,
                providerId,
                requestedCapabilities,
                skillIds,
                skillSelectionMode,
                artifacts,
                uploadedFiles,
                List.of(),
                recentMessages,
                sessionSummary,
                longTermMemory,
                chatRouteKind,
                skillRuntimeSnapshot,
                toolUseLimits,
                activeSkillIds,
                selectedDocumentIds
        );
    }
}
