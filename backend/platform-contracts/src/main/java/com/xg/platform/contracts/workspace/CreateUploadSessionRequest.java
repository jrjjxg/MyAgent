package com.xg.platform.contracts.workspace;

public record CreateUploadSessionRequest(
        String fileName,
        String contentType,
        Long totalSize,
        Integer chunkSize
) {
}
