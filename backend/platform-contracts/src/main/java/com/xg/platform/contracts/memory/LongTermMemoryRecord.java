package com.xg.platform.contracts.memory;

import java.io.Serializable;
import java.time.Instant;

public record LongTermMemoryRecord(
        String memoryId,
        String userId,
        LongTermMemoryType memoryType,
        String canonicalKey,
        String title,
        String content,
        String sourceThreadId,
        String sourceMessageId,
        String sourceTaskId,
        LongTermMemoryStatus status,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
