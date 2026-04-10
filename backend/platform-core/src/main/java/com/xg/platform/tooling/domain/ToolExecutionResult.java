package com.xg.platform.tooling.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolExecutionResult(
        String toolName,
        JsonNode output,
        boolean error,
        String message
) {
}
