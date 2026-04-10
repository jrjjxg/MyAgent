package com.xg.platform.workspace.application;

import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.workspace.ArtifactType;
import com.xg.platform.contracts.workspace.ArtifactVisibility;
import com.xg.platform.contracts.workspace.RegisterArtifactCommand;
import com.xg.platform.contracts.shared.validation.PlatformIds;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.workspace.application.ThreadService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadService {

    private final WorkspaceManager workspaceManager;
    private final ArtifactService artifactService;
    private final ThreadService threadRuntimeService;

    public UploadService(WorkspaceManager workspaceManager,
                         ArtifactService artifactService,
                         ThreadService threadRuntimeService) {
        this.workspaceManager = workspaceManager;
        this.artifactService = artifactService;
        this.threadRuntimeService = threadRuntimeService;
    }

    public ArtifactRecord upload(String userId,
                                 String threadId,
                                 String originalFilename,
                                 String contentType,
                                 byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        return upload(userId, threadId, originalFilename, contentType, new java.io.ByteArrayInputStream(content));
    }

    public ArtifactRecord upload(String userId,
                                 String threadId,
                                 String originalFilename,
                                 String contentType,
                                 InputStream contentStream) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return uploadToWorkspace(userId, workspaceId, threadId, originalFilename, contentType, contentStream);
    }

    public ArtifactRecord uploadToWorkspace(String userId,
                                            String workspaceId,
                                            String sourceThreadId,
                                            String originalFilename,
                                            String contentType,
                                            byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        return uploadToWorkspace(userId, workspaceId, sourceThreadId, originalFilename, contentType, new java.io.ByteArrayInputStream(content));
    }

    public ArtifactRecord uploadToWorkspace(String userId,
                                            String workspaceId,
                                            String sourceThreadId,
                                            String originalFilename,
                                            String contentType,
                                            InputStream contentStream) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        String sanitizedFilename = sanitizeFilename(originalFilename);
        Path uploadPath = workspaceManager.resolveWorkspacePath(safeUserId, safeWorkspaceId, WorkspaceArea.UPLOADS, sanitizedFilename);
        try {
            Files.createDirectories(uploadPath.getParent());
            Files.copy(contentStream, uploadPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to store upload", exception);
        }

        return registerUploadArtifact(safeUserId, safeWorkspaceId, sourceThreadId, sanitizedFilename, contentType);
    }

    public ArtifactRecord promoteUpload(String userId,
                                        String threadId,
                                        String originalFilename,
                                        String contentType,
                                        Path stagedPath) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return promoteUploadToWorkspace(userId, workspaceId, threadId, originalFilename, contentType, stagedPath);
    }

    public ArtifactRecord promoteUploadToWorkspace(String userId,
                                                   String workspaceId,
                                                   String sourceThreadId,
                                                   String originalFilename,
                                                   String contentType,
                                                   Path stagedPath) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        String sanitizedFilename = sanitizeFilename(originalFilename);
        Path uploadPath = workspaceManager.resolveWorkspacePath(safeUserId, safeWorkspaceId, WorkspaceArea.UPLOADS, sanitizedFilename);
        try {
            Files.createDirectories(uploadPath.getParent());
            Files.move(stagedPath, uploadPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to finalize staged upload", exception);
        }

        return registerUploadArtifact(safeUserId, safeWorkspaceId, sourceThreadId, sanitizedFilename, contentType);
    }

    private ArtifactRecord registerUploadArtifact(String userId,
                                                  String workspaceId,
                                                  String sourceThreadId,
                                                  String sanitizedFilename,
                                                  String contentType) {
        return artifactService.register(new RegisterArtifactCommand(
                userId,
                workspaceId,
                sourceThreadId,
                sanitizedFilename,
                ArtifactType.UPLOAD,
                ArtifactVisibility.USER_VISIBLE,
                WorkspaceArea.UPLOADS,
                sanitizedFilename,
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType
        ));
    }

    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Uploaded file name must not be blank");
        }
        String basename = originalFilename.replace('\\', '/');
        int separatorIndex = basename.lastIndexOf('/');
        if (separatorIndex >= 0) {
            basename = basename.substring(separatorIndex + 1);
        }
        basename = basename.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        while (basename.startsWith(".")) {
            basename = basename.substring(1);
        }
        if (basename.isBlank()) {
            throw new IllegalArgumentException("Uploaded file name is invalid");
        }
        if (basename.length() > 120) {
            int extensionIndex = basename.lastIndexOf('.');
            if (extensionIndex > 0 && extensionIndex >= basename.length() - 16) {
                String extension = basename.substring(extensionIndex);
                basename = basename.substring(0, 120 - extension.length()) + extension;
            } else {
                basename = basename.substring(0, 120);
            }
        }
        return basename;
    }
}
