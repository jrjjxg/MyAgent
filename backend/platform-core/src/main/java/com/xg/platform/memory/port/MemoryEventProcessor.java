package com.xg.platform.memory.port;

public interface MemoryEventProcessor {

    void process(MemoryEventPayload payload);
}
