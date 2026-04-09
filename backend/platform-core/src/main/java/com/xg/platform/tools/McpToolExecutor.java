package com.xg.platform.tools;

public class McpToolExecutor {

    public ToolExecutionResult execute(ToolExecutionRequest request) {
        throw new IllegalStateException("MCP tool execution is not implemented for tool: " + request.tool().name());
    }
}
