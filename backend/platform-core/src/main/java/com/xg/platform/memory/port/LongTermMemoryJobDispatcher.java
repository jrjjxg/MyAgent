package com.xg.platform.memory.port;

public interface LongTermMemoryJobDispatcher {

    void dispatch(LongTermMemoryExtractionRequest request);
}
