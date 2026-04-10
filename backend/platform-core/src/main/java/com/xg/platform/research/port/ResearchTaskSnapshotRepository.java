package com.xg.platform.research.port;

import com.xg.platform.contracts.research.ResearchTaskSnapshotRecord;

import java.util.Optional;

public interface ResearchTaskSnapshotRepository {

    Optional<ResearchTaskSnapshotRecord> findByTask(String userId, String threadId, String taskId);

    ResearchTaskSnapshotRecord save(String userId, ResearchTaskSnapshotRecord record);

    void deleteByTask(String userId, String taskId);

    void deleteByThread(String userId, String threadId);
}
