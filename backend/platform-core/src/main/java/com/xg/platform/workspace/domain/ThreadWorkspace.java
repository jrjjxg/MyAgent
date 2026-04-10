package com.xg.platform.workspace.domain;

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
