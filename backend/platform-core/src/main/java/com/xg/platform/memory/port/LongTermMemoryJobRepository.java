package com.xg.platform.memory.port;

import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;

import java.util.Optional;

public interface LongTermMemoryJobRepository {

    Optional<MemoryExtractionJobRecord> createQueuedIfAbsent(String userId,
                                                             String threadId,
                                                             String messageId,
                                                             String extractorVersion,
                                                             int eligibleTurnCount);

    Optional<MemoryExtractionJobRecord> findById(String jobId);

    Optional<MemoryExtractionJobRecord> findLatestSucceeded(String userId, String threadId, String extractorVersion);

    boolean hasPendingJob(String userId, String threadId, String extractorVersion);

    Optional<MemoryExtractionJobRecord> markRunning(String jobId);

    MemoryExtractionJobRecord markSucceeded(String jobId);

    MemoryExtractionJobRecord markFailure(String jobId, String lastError, boolean terminal);

    void deleteByThread(String userId, String threadId);
}
