package com.xg.platform.memory.application;

import com.xg.platform.contracts.memory.CachedThreadMemoryRecord;
import com.xg.platform.memory.port.ThreadMemoryViewCache;

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
