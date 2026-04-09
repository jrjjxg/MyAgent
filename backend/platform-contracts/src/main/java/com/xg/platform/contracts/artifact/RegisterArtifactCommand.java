package com.xg.platform.contracts.artifact;

import com.xg.platform.contracts.workspace.WorkspaceArea;

public record RegisterArtifactCommand(
        String userId,
        String workspaceId,
        String sourceThreadId,
        String name,
        ArtifactType type,
        ArtifactVisibility visibility,
        WorkspaceArea area,
        String relativePath,
        String contentType
) {
}
