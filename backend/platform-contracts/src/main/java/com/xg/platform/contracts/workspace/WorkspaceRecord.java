package com.xg.platform.contracts.workspace;

import java.time.Instant;

public record WorkspaceRecord(
        String workspaceId,
        String userId,
        String title,
        WorkspaceStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
