package com.xg.platform.tools;

import com.fasterxml.jackson.databind.JsonNode;

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

    public ToolExecutionRequest(String userId,
                                String threadId,
                                String runId,
                                ToolDescriptor tool,
                                JsonNode arguments) {
        this(userId, threadId, runId, tool, arguments, null, java.util.List.of(), java.util.List.of());
    }

    public ToolExecutionRequest(String userId,
                                String threadId,
                                String runId,
                                ToolDescriptor tool,
                                JsonNode arguments,
                                SkillRuntimeSnapshot skillRuntimeSnapshot) {
        this(userId, threadId, runId, tool, arguments, skillRuntimeSnapshot, java.util.List.of(), java.util.List.of());
    }

    public ToolExecutionRequest(String userId,
                                String threadId,
                                String runId,
                                ToolDescriptor tool,
                                JsonNode arguments,
                                SkillRuntimeSnapshot skillRuntimeSnapshot,
                                java.util.List<String> activeSkillIds) {
        this(userId, threadId, runId, tool, arguments, skillRuntimeSnapshot, activeSkillIds, java.util.List.of());
    }
}
