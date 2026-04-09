package com.xg.platform.contracts.memory;

import java.time.Instant;

public record StableFactMemoryRecord(
        String memoryId,
        String userId,
        String factType,
        String category,
        String title,
        String content,
        String fact,
        String sourceThreadId,
        String sourceTaskId,
        LongTermMemoryStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
