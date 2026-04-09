package com.xg.platform.contracts.document;

import java.io.Serializable;
import java.time.Instant;

public record DocumentRecord(
        String documentId,
        String workspaceId,
        String sourceThreadId,
        String sourceArtifactId,
        String name,
        DocumentStatus status,
        String primaryTextArtifactId,
        String chunkIndexArtifactId,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
