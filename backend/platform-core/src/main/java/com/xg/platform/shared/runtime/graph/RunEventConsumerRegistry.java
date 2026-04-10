package com.xg.platform.shared.runtime.graph;

import com.xg.platform.contracts.shared.event.RunEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RunEventConsumerRegistry {

    private static final Consumer<RunEvent> NO_OP_CONSUMER = event -> {
    };

    private final Map<String, Consumer<RunEvent>> consumers = new ConcurrentHashMap<>();

    public void register(String runContextKey, Consumer<RunEvent> consumer) {
        consumers.put(runContextKey, consumer == null ? NO_OP_CONSUMER : consumer);
    }

    public Consumer<RunEvent> resolve(String runContextKey) {
        if (runContextKey == null || runContextKey.isBlank()) {
            return NO_OP_CONSUMER;
        }
        return consumers.getOrDefault(runContextKey, NO_OP_CONSUMER);
    }

    public void remove(String runContextKey) {
        if (runContextKey != null) {
            consumers.remove(runContextKey);
        }
    }
}
