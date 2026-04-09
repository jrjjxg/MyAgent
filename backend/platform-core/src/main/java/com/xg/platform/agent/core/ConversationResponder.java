package com.xg.platform.agent.core;

public interface ConversationResponder {

    AgentExecutionResult respond(AgentExecutionRequest request, AgentOutputEmitter outputEmitter);
}
