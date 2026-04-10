package com.xg.platform.api.messaging.local;

import com.xg.platform.memory.port.MemoryEventPayload;
import com.xg.platform.memory.port.MemoryEventProcessor;
import com.xg.platform.memory.port.MemoryEventPublisher;

import java.util.concurrent.Executor;

public class LocalMemoryEventPublisher implements MemoryEventPublisher {

    private final Executor executor;
    private final MemoryEventProcessor memoryEventProcessor;

    public LocalMemoryEventPublisher(Executor executor, MemoryEventProcessor memoryEventProcessor) {
        this.executor = executor;
        this.memoryEventProcessor = memoryEventProcessor;
    }

    @Override
    public void publish(MemoryEventPayload payload) {
        executor.execute(() -> memoryEventProcessor.process(payload));
    }
}
