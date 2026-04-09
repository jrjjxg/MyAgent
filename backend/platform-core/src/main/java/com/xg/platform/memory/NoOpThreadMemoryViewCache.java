package com.xg.platform.memory;

import com.xg.platform.contracts.memory.CachedThreadMemoryRecord;
import com.xg.platform.runtime.ThreadMemoryViewCache;

import java.util.Optional;

public class NoOpThreadMemoryViewCache implements ThreadMemoryViewCache {

    @Override
    public Optional<CachedThreadMemoryRecord> get(String userId, String threadId) {
        return Optional.empty();
    }

    @Override
    public void put(String userId, CachedThreadMemoryRecord record) {
    }
}
