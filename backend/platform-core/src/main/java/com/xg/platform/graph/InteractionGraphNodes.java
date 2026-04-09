package com.xg.platform.graph;

import com.xg.platform.contracts.message.RunEvent;

import java.util.Map;
import java.util.function.Consumer;

public interface InteractionGraphNodes {

    Map<String, Object> loadShortTermMemory(InteractionState state);

    Map<String, Object> loadLongTermMemory(InteractionState state);

    Map<String, Object> loadDraftContext(InteractionState state);

    Map<String, Object> routeInteraction(InteractionState state);

    default Map<String, Object> prepareAgentStep(InteractionState state) {
        return prepareAgentStep(state, event -> {
        });
    }

    Map<String, Object> prepareAgentStep(InteractionState state, Consumer<RunEvent> runEventConsumer);

    Map<String, Object> agent(InteractionState state);

    default Map<String, Object> executeTools(InteractionState state) {
        return executeTools(state, event -> {
        });
    }

    Map<String, Object> executeTools(InteractionState state, Consumer<RunEvent> runEventConsumer);

    default Map<String, Object> runScopingFrame(InteractionState state) {
        return runScopingFrame(state, event -> {
        });
    }

    Map<String, Object> runScopingFrame(InteractionState state, Consumer<RunEvent> runEventConsumer);

    Map<String, Object> persistDraft(InteractionState state);

    default Map<String, Object> persistAssistantMessage(InteractionState state) {
        return persistAssistantMessage(state, event -> {
        });
    }

    Map<String, Object> persistAssistantMessage(InteractionState state, Consumer<RunEvent> runEventConsumer);

    default Map<String, Object> persistTurnArtifacts(InteractionState state) {
        return persistTurnArtifacts(state, event -> {
        });
    }

    Map<String, Object> persistTurnArtifacts(InteractionState state, Consumer<RunEvent> runEventConsumer);

    default Map<String, Object> publishTurnEvents(InteractionState state) {
        return publishTurnEvents(state, event -> {
        });
    }

    Map<String, Object> publishTurnEvents(InteractionState state, Consumer<RunEvent> runEventConsumer);
}
