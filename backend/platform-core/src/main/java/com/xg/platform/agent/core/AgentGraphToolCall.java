package com.xg.platform.agent.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;

public record AgentGraphToolCall(
        String id,
        String name,
        JsonNode arguments
) implements Serializable {
}
