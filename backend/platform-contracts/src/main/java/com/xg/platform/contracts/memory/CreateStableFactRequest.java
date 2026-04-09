package com.xg.platform.contracts.memory;

public record CreateStableFactRequest(
        String factType,
        String title,
        String content,
        String sourceThreadId,
        String sourceTaskId
) {
}
