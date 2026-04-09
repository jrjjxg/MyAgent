package com.xg.platform.api.upload;

public record ChunkUploadProgress(
        ChunkUploadSession session,
        int uploadedChunks,
        boolean alreadyUploaded
) {
}
