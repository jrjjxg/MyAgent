package com.xg.platform.contracts.memory;

public record UpdateLongTermMemoryRequest(
        LongTermMemoryType memoryType,
        String canonicalKey,
        String title,
        String content,
        String sourceThreadId,
        String sourceMessageId,
        String sourceTaskId
) {
}
