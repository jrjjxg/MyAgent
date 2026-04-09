package com.xg.platform.api.runtime;

import com.xg.platform.runtime.MemoryEventPayload;
import com.xg.platform.runtime.MemoryEventProcessor;
import com.xg.platform.runtime.MemoryEventPublisher;

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
