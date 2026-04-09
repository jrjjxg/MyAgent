package com.xg.platform.contracts.artifact;

public record UploadResponse(
        ArtifactRecord artifact,
        String documentId,
        String ingestTaskId
) {
}
