package com.xg.platform.contracts.memory;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateLongTermMemoryRequest(
        LongTermMemoryType memoryType,
        String canonicalKey,
        String title,
        String content,
        JsonNode valueJson,
        String sourceThreadId,
        String sourceMessageId,
        String sourceTaskId
) {
}
