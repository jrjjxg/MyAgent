package com.xg.platform.contracts.artifact;

import java.time.Instant;

public record UploadSessionStatusResponse(
        String uploadId,
        String workspaceId,
        String sourceThreadId,
        String fileName,
        String contentType,
        long totalSize,
        int chunkSize,
        int totalChunks,
        int uploadedChunks,
        boolean completed,
        String artifactId,
        String documentId,
        String ingestTaskId,
        Instant createdAt,
        Instant updatedAt
) {
}
