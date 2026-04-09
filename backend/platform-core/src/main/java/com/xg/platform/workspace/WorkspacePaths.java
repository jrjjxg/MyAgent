package com.xg.platform.workspace;

import java.nio.file.Path;

public record WorkspacePaths(
        String userId,
        String workspaceId,
        Path root,
        Path uploads,
        Path workspace
) {
}
