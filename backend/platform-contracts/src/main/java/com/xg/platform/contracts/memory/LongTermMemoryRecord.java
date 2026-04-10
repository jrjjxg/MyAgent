package com.xg.platform.contracts.memory;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;
import java.time.Instant;

public record LongTermMemoryRecord(
        String memoryId,
        String userId,
        LongTermMemoryType memoryType,
        String canonicalKey,
        String title,
        String content,
        JsonNode valueJson,
        String sourceThreadId,
        String sourceMessageId,
        String sourceTaskId,
        LongTermMemoryStatus status,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
