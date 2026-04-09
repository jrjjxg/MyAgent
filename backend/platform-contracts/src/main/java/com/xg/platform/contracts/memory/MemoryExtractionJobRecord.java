package com.xg.platform.contracts.memory;

import java.io.Serializable;
import java.time.Instant;

public record MemoryExtractionJobRecord(
        String jobId,
        String userId,
        String threadId,
        String messageId,
        String extractorVersion,
        MemoryExtractionJobStatus status,
        int attemptCount,
        String lastError,
        Integer eligibleTurnCount,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
) implements Serializable {
}
