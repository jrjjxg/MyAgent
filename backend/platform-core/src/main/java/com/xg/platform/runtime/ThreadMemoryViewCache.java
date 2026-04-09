package com.xg.platform.runtime;

import com.xg.platform.contracts.memory.CachedThreadMemoryRecord;

import java.util.Optional;

public interface ThreadMemoryViewCache {

    Optional<CachedThreadMemoryRecord> get(String userId, String threadId);

    void put(String userId, CachedThreadMemoryRecord record);
}
