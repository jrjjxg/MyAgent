package com.xg.platform.tooling.application;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;

public class McpToolExecutor {

    public ToolExecutionResult execute(ToolExecutionRequest request) {
        throw new IllegalStateException("MCP tool execution is not implemented for tool: " + request.tool().name());
    }
}
