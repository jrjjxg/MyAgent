package com.xg.platform.graph;

import com.xg.platform.contracts.message.RunEvent;

import java.util.Map;
import java.util.function.Consumer;

public interface ChatGraphNodes {

    Map<String, Object> loadShortTermMemory(ChatState state);

    Map<String, Object> loadLongTermMemory(ChatState state);

    default Map<String, Object> execute(ChatState state) {
        return execute(state, event -> {
        });
    }

    Map<String, Object> execute(ChatState state, Consumer<RunEvent> runEventConsumer);
}
