package com.xg.platform.api.upload;

import java.time.Instant;

public record ChunkUploadSession(
        String uploadId,
        String userId,
        String workspaceId,
        String sourceThreadId,
        String fileName,
        String contentType,
        long totalSize,
        int chunkSize,
        int totalChunks,
        String stagedRelativePath,
        boolean completed,
        String artifactId,
        String documentId,
        String ingestTaskId,
        Instant createdAt,
        Instant updatedAt
) {

    public ChunkUploadSession touch(Instant timestamp) {
        return new ChunkUploadSession(
                uploadId,
                userId,
                workspaceId,
                sourceThreadId,
                fileName,
                contentType,
                totalSize,
                chunkSize,
                totalChunks,
                stagedRelativePath,
                completed,
                artifactId,
                documentId,
                ingestTaskId,
                createdAt,
                timestamp
        );
    }

    public ChunkUploadSession markCompleted(String newArtifactId,
                                            String newDocumentId,
                                            String newIngestTaskId,
                                            Instant timestamp) {
        return new ChunkUploadSession(
                uploadId,
                userId,
                workspaceId,
                sourceThreadId,
                fileName,
                contentType,
                totalSize,
                chunkSize,
                totalChunks,
                stagedRelativePath,
                true,
                newArtifactId,
                newDocumentId,
                newIngestTaskId,
                createdAt,
                timestamp
        );
    }
}
