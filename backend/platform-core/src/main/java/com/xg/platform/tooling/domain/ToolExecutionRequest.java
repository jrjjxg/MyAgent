package com.xg.platform.tooling.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.xg.platform.skill.runtime.SkillRuntimeSnapshot;

public record ToolExecutionRequest(
        String userId,
        String threadId,
        String runId,
        ToolDescriptor tool,
        JsonNode arguments,
        SkillRuntimeSnapshot skillRuntimeSnapshot,
        java.util.List<String> activeSkillIds,
        java.util.List<String> allowedDocumentIds
) {
    public ToolExecutionRequest {
        activeSkillIds = activeSkillIds == null ? java.util.List.of() : java.util.List.copyOf(activeSkillIds);
        allowedDocumentIds = allowedDocumentIds == null ? java.util.List.of() : java.util.List.copyOf(allowedDocumentIds);
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
        private ToolDescriptor tool;
        private JsonNode arguments;
        private SkillRuntimeSnapshot skillRuntimeSnapshot;
        private java.util.List<String> activeSkillIds = java.util.List.of();
        private java.util.List<String> allowedDocumentIds = java.util.List.of();

        private Builder() {
        }

        private Builder(ToolExecutionRequest request) {
            this.userId = request.userId();
            this.threadId = request.threadId();
            this.runId = request.runId();
            this.tool = request.tool();
            this.arguments = request.arguments();
            this.skillRuntimeSnapshot = request.skillRuntimeSnapshot();
            this.activeSkillIds = request.activeSkillIds();
            this.allowedDocumentIds = request.allowedDocumentIds();
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

        public Builder tool(ToolDescriptor tool) {
            this.tool = tool;
            return this;
        }

        public Builder arguments(JsonNode arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder skillRuntimeSnapshot(SkillRuntimeSnapshot skillRuntimeSnapshot) {
            this.skillRuntimeSnapshot = skillRuntimeSnapshot;
            return this;
        }

        public Builder activeSkillIds(java.util.List<String> activeSkillIds) {
            this.activeSkillIds = activeSkillIds;
            return this;
        }

        public Builder allowedDocumentIds(java.util.List<String> allowedDocumentIds) {
            this.allowedDocumentIds = allowedDocumentIds;
            return this;
        }

        public ToolExecutionRequest build() {
            return new ToolExecutionRequest(
                    userId,
                    threadId,
                    runId,
                    tool,
                    arguments,
                    skillRuntimeSnapshot,
                    activeSkillIds,
                    allowedDocumentIds
            );
        }
    }
}
