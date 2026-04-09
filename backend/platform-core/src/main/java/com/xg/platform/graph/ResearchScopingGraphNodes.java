package com.xg.platform.graph;

import com.xg.platform.contracts.message.RunEvent;

import java.util.Map;
import java.util.function.Consumer;

public interface ResearchScopingGraphNodes {

    Map<String, Object> loadShortTermMemory(ResearchScopingState state);

    Map<String, Object> loadLongTermMemory(ResearchScopingState state);

    Map<String, Object> loadCurrentDraft(ResearchScopingState state);

    default Map<String, Object> runScopingFrame(ResearchScopingState state) {
        return runScopingFrame(state, event -> {
        });
    }

    Map<String, Object> runScopingFrame(ResearchScopingState state, Consumer<RunEvent> runEventConsumer);

    Map<String, Object> persistDraft(ResearchScopingState state);

    default Map<String, Object> persistAssistantMessage(ResearchScopingState state) {
        return persistAssistantMessage(state, event -> {
        });
    }

    Map<String, Object> persistAssistantMessage(ResearchScopingState state, Consumer<RunEvent> runEventConsumer);

    default Map<String, Object> publishScopingEvents(ResearchScopingState state) {
        return publishScopingEvents(state, event -> {
        });
    }

    Map<String, Object> publishScopingEvents(ResearchScopingState state, Consumer<RunEvent> runEventConsumer);
}
