package com.xg.platform.contracts.conversation;

public record ThreadFileReference(
        String name,
        String relativePath,
        String absolutePath,
        String contentType,
        long sizeBytes
) {
}
