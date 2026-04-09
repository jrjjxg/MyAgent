package com.xg.platform.contracts.message;

public record ThreadFileReference(
        String name,
        String relativePath,
        String absolutePath,
        String contentType,
        long sizeBytes
) {
}
