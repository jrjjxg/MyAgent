package com.xg.platform.api.upload;

import java.util.Optional;

public interface ChunkUploadStateStore {

    ChunkUploadSession create(ChunkUploadSession session);

    Optional<ChunkUploadSession> find(String userId, String workspaceId, String uploadId);

    int countUploadedChunks(String userId, String workspaceId, String uploadId);

    ChunkUploadProgress markChunkUploaded(String userId, String workspaceId, String uploadId, int chunkIndex);

    ChunkUploadSession markCompleted(String userId,
                                     String workspaceId,
                                     String uploadId,
                                     String artifactId,
                                     String documentId,
                                     String ingestTaskId);
}
