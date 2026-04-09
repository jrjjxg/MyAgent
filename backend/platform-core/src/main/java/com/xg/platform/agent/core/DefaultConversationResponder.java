package com.xg.platform.agent.core;

public class DefaultConversationResponder implements ConversationResponder {

    private final AgentOrchestrator agentOrchestrator;

    public DefaultConversationResponder(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @Override
    public AgentExecutionResult respond(AgentExecutionRequest request, AgentOutputEmitter outputEmitter) {
        return agentOrchestrator.execute(AgentMode.GENERAL, request, outputEmitter);
    }
}
