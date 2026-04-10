package com.xg.platform.agent.core;

import com.xg.platform.contracts.shared.agent.AgentCapability;
import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import com.xg.platform.conversation.domain.ConversationRouteKind;
import com.xg.platform.skill.runtime.SkillRuntimeSnapshot;

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
        ConversationRouteKind chatRouteKind,
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

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private String userId;
        private String threadId;
        private String runId;
        private String message;
        private String agentId;
        private String providerId;
        private List<AgentCapability> requestedCapabilities = List.of();
        private List<String> skillIds = List.of();
        private String skillSelectionMode;
        private List<ArtifactRecord> artifacts = List.of();
        private List<ThreadFileReference> uploadedFiles = List.of();
        private List<ThreadFileReference> inputImages = List.of();
        private List<MessageRecord> recentMessages = List.of();
        private String sessionSummary;
        private String longTermMemory;
        private ConversationRouteKind chatRouteKind;
        private SkillRuntimeSnapshot skillRuntimeSnapshot;
        private ToolUseLimits toolUseLimits;
        private List<String> activeSkillIds = List.of();
        private List<String> selectedDocumentIds = List.of();

        private Builder() {
        }

        private Builder(AgentExecutionRequest request) {
            this.userId = request.userId();
            this.threadId = request.threadId();
            this.runId = request.runId();
            this.message = request.message();
            this.agentId = request.agentId();
            this.providerId = request.providerId();
            this.requestedCapabilities = request.requestedCapabilities();
            this.skillIds = request.skillIds();
            this.skillSelectionMode = request.skillSelectionMode();
            this.artifacts = request.artifacts();
            this.uploadedFiles = request.uploadedFiles();
            this.inputImages = request.inputImages();
            this.recentMessages = request.recentMessages();
            this.sessionSummary = request.sessionSummary();
            this.longTermMemory = request.longTermMemory();
            this.chatRouteKind = request.chatRouteKind();
            this.skillRuntimeSnapshot = request.skillRuntimeSnapshot();
            this.toolUseLimits = request.toolUseLimits();
            this.activeSkillIds = request.activeSkillIds();
            this.selectedDocumentIds = request.selectedDocumentIds();
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder threadId(String threadId) {
            this.threadId = threadId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Builder requestedCapabilities(List<AgentCapability> requestedCapabilities) {
            this.requestedCapabilities = requestedCapabilities;
            return this;
        }

        public Builder skillIds(List<String> skillIds) {
            this.skillIds = skillIds;
            return this;
        }

        public Builder skillSelectionMode(String skillSelectionMode) {
            this.skillSelectionMode = skillSelectionMode;
            return this;
        }

        public Builder artifacts(List<ArtifactRecord> artifacts) {
            this.artifacts = artifacts;
            return this;
        }

        public Builder uploadedFiles(List<ThreadFileReference> uploadedFiles) {
            this.uploadedFiles = uploadedFiles;
            return this;
        }

        public Builder inputImages(List<ThreadFileReference> inputImages) {
            this.inputImages = inputImages;
            return this;
        }

        public Builder recentMessages(List<MessageRecord> recentMessages) {
            this.recentMessages = recentMessages;
            return this;
        }

        public Builder sessionSummary(String sessionSummary) {
            this.sessionSummary = sessionSummary;
            return this;
        }

        public Builder longTermMemory(String longTermMemory) {
            this.longTermMemory = longTermMemory;
            return this;
        }

        public Builder chatRouteKind(ConversationRouteKind chatRouteKind) {
            this.chatRouteKind = chatRouteKind;
            return this;
        }

        public Builder skillRuntimeSnapshot(SkillRuntimeSnapshot skillRuntimeSnapshot) {
            this.skillRuntimeSnapshot = skillRuntimeSnapshot;
            return this;
        }

        public Builder toolUseLimits(ToolUseLimits toolUseLimits) {
            this.toolUseLimits = toolUseLimits;
            return this;
        }

        public Builder activeSkillIds(List<String> activeSkillIds) {
            this.activeSkillIds = activeSkillIds;
            return this;
        }

        public Builder selectedDocumentIds(List<String> selectedDocumentIds) {
            this.selectedDocumentIds = selectedDocumentIds;
            return this;
        }

        public AgentExecutionRequest build() {
            return new AgentExecutionRequest(
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
                    activeSkillIds,
                    selectedDocumentIds
            );
        }
    }
}
