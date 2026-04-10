package com.xg.platform.contracts.workspace;

public record UploadResponse(
        ArtifactRecord artifact,
        String documentId,
        String ingestTaskId
) {
}
