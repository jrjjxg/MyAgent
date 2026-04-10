package com.xg.platform.agent.core;

import java.util.Map;
import java.util.List;

public record AgentModelStep(
        String content,
        List<AgentGraphToolCall> toolCalls,
        Map<String, Object> assistantProperties
) {
    public AgentModelStep {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        assistantProperties = assistantProperties == null ? Map.of() : Map.copyOf(assistantProperties);
    }

    public static AgentModelStep of(String content, List<AgentGraphToolCall> toolCalls) {
        return new AgentModelStep(content, toolCalls, Map.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
