package com.xg.platform.document.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.workspace.application.WorkspaceManager;
import com.xg.platform.contracts.workspace.WorkspaceArea;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.xg.platform.document.domain.DocumentChunk;

public class ChunkIndexStore {

    private static final TypeReference<List<DocumentChunk>> CHUNK_LIST_TYPE = new TypeReference<>() {
    };

    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;

    public ChunkIndexStore(WorkspaceManager workspaceManager, ObjectMapper objectMapper) {
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
    }

    public Path writeChunkIndex(String userId, String workspaceId, String relativePath, List<DocumentChunk> chunks) {
        Path path = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.WORKSPACE, relativePath);
        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), chunks);
            return path;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write chunk index", exception);
        }
    }

    public List<DocumentChunk> readChunkIndex(String userId, String workspaceId, String relativePath) {
        Path path = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.WORKSPACE, relativePath);
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), CHUNK_LIST_TYPE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read chunk index", exception);
        }
    }
}
