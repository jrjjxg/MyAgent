package com.xg.platform.contracts.workspace;

import java.time.Instant;

public record ThreadRecord(
        String threadId,
        String userId,
        String workspaceId,
        String title,
        ThreadStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
