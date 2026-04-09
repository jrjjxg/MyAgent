package com.xg.platform.contracts.artifact;

public record CreateUploadSessionRequest(
        String fileName,
        String contentType,
        Long totalSize,
        Integer chunkSize
) {
}
