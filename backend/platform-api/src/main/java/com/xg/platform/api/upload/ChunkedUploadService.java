package com.xg.platform.api.upload;

import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.contracts.workspace.CompleteUploadSessionResponse;
import com.xg.platform.contracts.workspace.CreateUploadSessionRequest;
import com.xg.platform.contracts.workspace.UploadResponse;
import com.xg.platform.contracts.workspace.UploadSessionStatusResponse;
import com.xg.platform.contracts.shared.validation.PlatformIds;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.WorkspaceService;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.UploadService;
import com.xg.platform.workspace.application.WorkspaceManager;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public class ChunkedUploadService {

    private final ThreadService threadRuntimeService;
    private final WorkspaceService workspaceRuntimeService;
    private final WorkspaceManager workspaceManager;
    private final UploadService uploadService;
    private final ArtifactService artifactService;
    private final DocumentIngestService documentIngestService;
    private final ChunkUploadStateStore stateStore;
    private final int maxChunkSizeBytes;

    public ChunkedUploadService(ThreadService threadRuntimeService,
                                WorkspaceService workspaceRuntimeService,
                                WorkspaceManager workspaceManager,
                                UploadService uploadService,
                                ArtifactService artifactService,
                                DocumentIngestService documentIngestService,
                                ChunkUploadStateStore stateStore,
                                int maxChunkSizeBytes) {
        this.threadRuntimeService = threadRuntimeService;
        this.workspaceRuntimeService = workspaceRuntimeService;
        this.workspaceManager = workspaceManager;
        this.uploadService = uploadService;
        this.artifactService = artifactService;
        this.documentIngestService = documentIngestService;
        this.stateStore = stateStore;
        this.maxChunkSizeBytes = maxChunkSizeBytes;
    }

    public UploadSessionStatusResponse createSession(String userId, String threadId, CreateUploadSessionRequest request) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return createSession(userId, workspaceId, threadId, request);
    }

    public UploadSessionStatusResponse createSession(String userId,
                                                     String workspaceId,
                                                     String sourceThreadId,
                                                     CreateUploadSessionRequest request) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        if (request == null) {
            throw new IllegalArgumentException("Upload session request must not be null");
        }
        if (request.totalSize() == null || request.totalSize() <= 0) {
            throw new IllegalArgumentException("Upload totalSize must be greater than zero");
        }
        if (request.chunkSize() == null || request.chunkSize() <= 0) {
            throw new IllegalArgumentException("Upload chunkSize must be greater than zero");
        }
        if (request.chunkSize() > maxChunkSizeBytes) {
            throw new IllegalArgumentException("Upload chunkSize exceeds the configured limit");
        }

        String uploadId = UUID.randomUUID().toString();
        String fileName = UploadService.sanitizeFilename(request.fileName());
        long totalSize = request.totalSize();
        int chunkSize = request.chunkSize();
        int totalChunks = Math.toIntExact((totalSize + chunkSize - 1) / chunkSize);
        Instant now = Instant.now();
        String stagedRelativePath = ".upload-sessions/%s/%s.part".formatted(
                PlatformIds.requireIdentifier(uploadId, "uploadId"),
                fileName
        );
        Path stagedPath = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.UPLOADS, stagedRelativePath);
        try {
            Files.createDirectories(stagedPath.getParent());
            try (RandomAccessFile file = new RandomAccessFile(stagedPath.toFile(), "rw")) {
                file.setLength(totalSize);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to initialize upload session", exception);
        }

        ChunkUploadSession session = new ChunkUploadSession(
                uploadId,
                userId,
                workspaceId,
                sourceThreadId,
                fileName,
                normalizeContentType(request.contentType()),
                totalSize,
                chunkSize,
                totalChunks,
                stagedRelativePath,
                false,
                null,
                null,
                null,
                now,
                now
        );
        stateStore.create(session);
        return toStatus(session, 0);
    }

    public UploadSessionStatusResponse getSessionStatus(String userId, String threadId, String uploadId) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return getSessionStatusByWorkspace(userId, workspaceId, uploadId);
    }

    public UploadSessionStatusResponse getSessionStatusByWorkspace(String userId, String workspaceId, String uploadId) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        ChunkUploadSession session = requireSession(userId, workspaceId, uploadId);
        return toStatus(session, stateStore.countUploadedChunks(userId, workspaceId, uploadId));
    }

    public UploadSessionStatusResponse uploadChunk(String userId,
                                                   String threadId,
                                                   String uploadId,
                                                   int chunkIndex,
                                                   byte[] chunkBytes) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return uploadChunkByWorkspace(userId, workspaceId, uploadId, chunkIndex, chunkBytes);
    }

    public UploadSessionStatusResponse uploadChunkByWorkspace(String userId,
                                                              String workspaceId,
                                                              String uploadId,
                                                              int chunkIndex,
                                                              byte[] chunkBytes) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        ChunkUploadSession session = requireSession(userId, workspaceId, uploadId);
        if (session.completed()) {
            return toStatus(session, stateStore.countUploadedChunks(userId, workspaceId, uploadId));
        }
        if (chunkIndex < 0 || chunkIndex >= session.totalChunks()) {
            throw new IllegalArgumentException("chunkIndex is out of range");
        }
        if (chunkBytes == null || chunkBytes.length == 0) {
            throw new IllegalArgumentException("Chunk payload must not be empty");
        }
        int expectedLength = expectedChunkLength(session, chunkIndex);
        if (chunkBytes.length != expectedLength) {
            throw new IllegalArgumentException("Chunk payload size does not match the expected chunk length");
        }

        Path stagedPath = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.UPLOADS, session.stagedRelativePath());
        long offset = (long) chunkIndex * session.chunkSize();
        try (RandomAccessFile file = new RandomAccessFile(stagedPath.toFile(), "rw")) {
            file.seek(offset);
            file.write(chunkBytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write upload chunk", exception);
        }

        ChunkUploadProgress progress = stateStore.markChunkUploaded(userId, workspaceId, uploadId, chunkIndex);
        return toStatus(progress.session(), progress.uploadedChunks());
    }

    public CompleteUploadSessionResponse completeSession(String userId, String threadId, String uploadId) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return completeSessionByWorkspace(userId, workspaceId, uploadId);
    }

    public CompleteUploadSessionResponse completeSessionByWorkspace(String userId, String workspaceId, String uploadId) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        ChunkUploadSession session = requireSession(userId, workspaceId, uploadId);
        int uploadedChunks = stateStore.countUploadedChunks(userId, workspaceId, uploadId);
        if (session.completed()) {
            var artifact = artifactService.findArtifactByWorkspace(userId, workspaceId, session.artifactId());
            return new CompleteUploadSessionResponse(
                    toStatus(session, uploadedChunks),
                    new UploadResponse(artifact, session.documentId(), session.ingestTaskId())
            );
        }
        if (uploadedChunks != session.totalChunks()) {
            throw new IllegalStateException("Upload session is incomplete");
        }

        Path stagedPath = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.UPLOADS, session.stagedRelativePath());
        var artifact = uploadService.promoteUploadToWorkspace(userId, workspaceId, session.sourceThreadId(), session.fileName(), session.contentType(), stagedPath);
        var ticket = documentIngestService.scheduleIngestion(userId, workspaceId, session.sourceThreadId(), artifact);
        ChunkUploadSession completed = stateStore.markCompleted(
                userId,
                workspaceId,
                uploadId,
                artifact.artifactId(),
                ticket.documentId(),
                ticket.ingestTaskId()
        );
        cleanupSessionDirectory(stagedPath.getParent());
        return new CompleteUploadSessionResponse(
                toStatus(completed, uploadedChunks),
                new UploadResponse(artifact, ticket.documentId(), ticket.ingestTaskId())
        );
    }

    private ChunkUploadSession requireSession(String userId, String workspaceId, String uploadId) {
        return stateStore.find(userId, workspaceId, uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload session not found: " + uploadId));
    }

    private int expectedChunkLength(ChunkUploadSession session, int chunkIndex) {
        long startOffset = (long) chunkIndex * session.chunkSize();
        long remaining = session.totalSize() - startOffset;
        return (int) Math.min(session.chunkSize(), remaining);
    }

    private UploadSessionStatusResponse toStatus(ChunkUploadSession session, int uploadedChunks) {
        return new UploadSessionStatusResponse(
                session.uploadId(),
                session.workspaceId(),
                session.sourceThreadId(),
                session.fileName(),
                session.contentType(),
                session.totalSize(),
                session.chunkSize(),
                session.totalChunks(),
                uploadedChunks,
                session.completed(),
                session.artifactId(),
                session.documentId(),
                session.ingestTaskId(),
                session.createdAt(),
                session.updatedAt()
        );
    }

    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private void cleanupSessionDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory);
            Path parent = directory.getParent();
            if (parent != null) {
                Files.deleteIfExists(parent);
            }
        } catch (IOException ignored) {
        }
    }
}
