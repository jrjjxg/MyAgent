package com.xg.platform.contracts.artifact;

import com.xg.platform.contracts.workspace.WorkspaceArea;

import java.time.Instant;

public record ArtifactRecord(
        String artifactId,
        String userId,
        String workspaceId,
        String sourceThreadId,
        String name,
        ArtifactType type,
        ArtifactVisibility visibility,
        WorkspaceArea area,
        String relativePath,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
}
