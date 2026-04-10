package com.xg.platform.conversation.runtime;

import com.xg.platform.contracts.shared.event.RunEvent;

import java.util.Map;
import java.util.function.Consumer;

final class ConversationPersistenceNodes {

    private final ConversationRuntimeSupport support;

    ConversationPersistenceNodes(ConversationRuntimeSupport support) {
        this.support = support;
    }

    Map<String, Object> runScopingFrame(InteractionState state) {
        return support.runScopingFrame(state);
    }

    Map<String, Object> runScopingFrame(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return support.runScopingFrame(state, runEventConsumer);
    }

    Map<String, Object> persistDraft(InteractionState state) {
        return support.persistDraft(state);
    }

    Map<String, Object> persistAssistantMessage(InteractionState state) {
        return support.persistAssistantMessage(state);
    }

    Map<String, Object> persistAssistantMessage(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return support.persistAssistantMessage(state, runEventConsumer);
    }

    Map<String, Object> persistTurnArtifacts(InteractionState state) {
        return support.persistTurnArtifacts(state);
    }

    Map<String, Object> persistTurnArtifacts(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return support.persistTurnArtifacts(state, runEventConsumer);
    }

    Map<String, Object> publishTurnEvents(InteractionState state) {
        return support.publishTurnEvents(state);
    }

    Map<String, Object> publishTurnEvents(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return support.publishTurnEvents(state, runEventConsumer);
    }
}
