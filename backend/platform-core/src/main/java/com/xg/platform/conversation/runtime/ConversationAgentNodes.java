package com.xg.platform.conversation.runtime;

import com.xg.platform.contracts.shared.event.RunEvent;

import java.util.Map;
import java.util.function.Consumer;

final class ConversationAgentNodes {

    private final ConversationRuntimeSupport support;

    ConversationAgentNodes(ConversationRuntimeSupport support) {
        this.support = support;
    }

    Map<String, Object> prepareAgentStep(InteractionState state) {
        return support.prepareAgentStep(state);
    }

    Map<String, Object> prepareAgentStep(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return support.prepareAgentStep(state, runEventConsumer);
    }

    Map<String, Object> agent(InteractionState state) {
        return support.agent(state);
    }

    Map<String, Object> executeTools(InteractionState state) {
        return support.executeTools(state);
    }

    Map<String, Object> executeTools(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return support.executeTools(state, runEventConsumer);
    }
}
