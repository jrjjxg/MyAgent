package com.xg.platform.workspace;

import java.nio.file.Path;

public record ThreadWorkspace(
        String userId,
        String threadId,
        Path root,
        Path uploads,
        Path workspace,
        Path outputs
) {
}
