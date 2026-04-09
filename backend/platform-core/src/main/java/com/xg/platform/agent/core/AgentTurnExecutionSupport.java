package com.xg.platform.agent.core;

import com.xg.platform.tools.ToolDescriptor;

import java.util.List;

public interface AgentTurnExecutionSupport {

    String resolveProviderId(String requestedProviderId);

    default String resolveProviderId(String userId, String requestedProviderId) {
        return resolveProviderId(requestedProviderId);
    }

    default String runTextTurn(String providerId, String prompt, String userMessage) {
        return runTextTurn(providerId, null, prompt, userMessage);
    }

    String runTextTurn(String providerId,
                       String modelOverride,
                       String prompt,
                       String userMessage);

    default String runTextTurn(String userId,
                               String providerId,
                               String modelOverride,
                               String prompt,
                               String userMessage) {
        return runTextTurn(providerId, modelOverride, prompt, userMessage);
    }

    default AgentModelStep runSingleStep(String providerId,
                                         AgentExecutionRequest request,
                                         List<AgentGraphMessage> messages,
                                         String currentUserGraphMessageId,
                                         String prompt,
                                         List<ToolDescriptor> availableTools) {
        throw new UnsupportedOperationException("Single-step model execution is not implemented");
    }

    default AgentModelStep runSingleStep(String providerId,
                                         AgentExecutionRequest request,
                                         List<AgentGraphMessage> messages,
                                         String currentUserGraphMessageId,
                                         String prompt,
                                         List<ToolDescriptor> availableTools,
                                         AgentOutputEmitter outputEmitter) {
        return runSingleStep(providerId, request, messages, currentUserGraphMessageId, prompt, availableTools);
    }

    String runModelLoop(String providerId,
                        AgentExecutionRequest request,
                        String prompt,
                        List<ToolDescriptor> availableTools,
                        AgentOutputEmitter outputEmitter);
}
