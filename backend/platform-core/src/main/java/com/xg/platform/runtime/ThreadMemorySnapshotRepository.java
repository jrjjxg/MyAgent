package com.xg.platform.runtime;

import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;

import java.util.Optional;

public interface ThreadMemorySnapshotRepository {

    Optional<ThreadMemorySnapshotRecord> findByThread(String userId, String threadId);

    ThreadMemorySnapshotRecord save(String userId, ThreadMemorySnapshotRecord record);

    void deleteByThread(String userId, String threadId);
}
