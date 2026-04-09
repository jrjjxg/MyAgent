package com.xg.platform.tools;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolExecutionResult(
        String toolName,
        JsonNode output,
        boolean error,
        String message
) {
}
