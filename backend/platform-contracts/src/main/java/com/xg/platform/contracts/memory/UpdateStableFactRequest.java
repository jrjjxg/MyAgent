package com.xg.platform.contracts.memory;

public record UpdateStableFactRequest(
        String factType,
        String title,
        String content,
        String sourceThreadId,
        String sourceTaskId
) {
}
