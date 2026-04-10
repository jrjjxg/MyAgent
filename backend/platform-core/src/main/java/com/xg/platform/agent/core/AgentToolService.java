package com.xg.platform.agent.core;

import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;

import java.util.List;

public interface AgentToolService {

    List<ToolDescriptor> listAvailableTools(String userId);

    ToolExecutionResult execute(ToolExecutionRequest request);

    default ToolDescriptor requireTool(String userId, String toolName) {
        return listAvailableTools(userId).stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));
    }
}
