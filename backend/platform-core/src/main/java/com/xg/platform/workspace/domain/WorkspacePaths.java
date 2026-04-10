package com.xg.platform.workspace.domain;

import java.nio.file.Path;

public record WorkspacePaths(
        String userId,
        String workspaceId,
        Path root,
        Path uploads,
        Path workspace
) {
}
