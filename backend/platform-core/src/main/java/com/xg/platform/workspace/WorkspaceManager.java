package com.xg.platform.workspace;

import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.validation.PlatformIds;
import com.xg.platform.contracts.workspace.WorkspaceArea;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class WorkspaceManager {

    private final Path dataRoot;

    public WorkspaceManager(Path dataRoot) {
        this.dataRoot = dataRoot.toAbsolutePath().normalize();
    }

    public WorkspacePaths ensureWorkspace(String userId, String workspaceId) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        Path root = workspaceRoot(safeUserId, safeWorkspaceId);
        Path uploads = root.resolve("uploads");
        Path workspace = root.resolve("workspace");
        try {
            Files.createDirectories(uploads);
            Files.createDirectories(workspace);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to initialize shared workspace", exception);
        }
        return new WorkspacePaths(safeUserId, safeWorkspaceId, root, uploads, workspace);
    }

    public ThreadWorkspace ensureThreadWorkspace(String userId, String threadId) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeThreadId = PlatformIds.requireThreadId(threadId);
        Path root = threadRoot(safeUserId, safeThreadId);
        Path uploads = root.resolve("uploads");
        Path workspace = root.resolve("workspace");
        Path outputs = root.resolve("outputs");
        try {
            Files.createDirectories(uploads);
            Files.createDirectories(workspace);
            Files.createDirectories(outputs);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to initialize thread workspace", exception);
        }
        return new ThreadWorkspace(safeUserId, safeThreadId, root, uploads, workspace, outputs);
    }

    public Path threadRoot(String userId, String threadId) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeThreadId = PlatformIds.requireThreadId(threadId);
        return dataRoot.resolve("users")
                .resolve(safeUserId)
                .resolve("threads")
                .resolve(safeThreadId)
                .normalize();
    }

    public Path workspaceRoot(String userId, String workspaceId) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        return dataRoot.resolve("users")
                .resolve(safeUserId)
                .resolve("workspaces")
                .resolve(safeWorkspaceId)
                .normalize();
    }

    public Path workspaceAreaRoot(String userId, String workspaceId, WorkspaceArea area) {
        WorkspacePaths workspacePaths = ensureWorkspace(userId, workspaceId);
        return switch (area) {
            case UPLOADS -> workspacePaths.uploads();
            case WORKSPACE -> workspacePaths.workspace();
            case OUTPUTS -> throw new IllegalArgumentException("Workspace-level OUTPUTS area is not supported");
        };
    }

    public Path areaRoot(String userId, String threadId, WorkspaceArea area) {
        ThreadWorkspace threadWorkspace = ensureThreadWorkspace(userId, threadId);
        return switch (area) {
            case UPLOADS -> threadWorkspace.uploads();
            case WORKSPACE -> threadWorkspace.workspace();
            case OUTPUTS -> threadWorkspace.outputs();
        };
    }

    public Path resolveWorkspacePath(String userId, String workspaceId, WorkspaceArea area, String relativePath) {
        Path areaRoot = workspaceAreaRoot(userId, workspaceId, area).toAbsolutePath().normalize();
        if (relativePath == null || relativePath.isBlank()) {
            return areaRoot;
        }
        Path normalizedRelative = PlatformIds.requireRelativePath(relativePath, "relativePath");
        Path resolved = areaRoot.resolve(normalizedRelative).normalize();
        if (!resolved.startsWith(areaRoot)) {
            throw new IllegalArgumentException("relativePath must stay inside the workspace");
        }
        return resolved;
    }

    public Path resolvePath(String userId, String threadId, WorkspaceArea area, String relativePath) {
        Path areaRoot = areaRoot(userId, threadId, area).toAbsolutePath().normalize();
        if (relativePath == null || relativePath.isBlank()) {
            return areaRoot;
        }
        Path normalizedRelative = PlatformIds.requireRelativePath(relativePath, "relativePath");
        Path resolved = areaRoot.resolve(normalizedRelative).normalize();
        if (!resolved.startsWith(areaRoot)) {
            throw new IllegalArgumentException("relativePath must stay inside the thread workspace");
        }
        return resolved;
    }

    public Path resolveArtifactPath(String userId, ArtifactRecord artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("artifact must not be null");
        }
        if (artifact.sourceThreadId() == null || artifact.sourceThreadId().isBlank()
                || artifact.area() == WorkspaceArea.UPLOADS) {
            return resolveWorkspacePath(userId, artifact.workspaceId(), artifact.area(), artifact.relativePath());
        }
        return resolvePath(userId, artifact.sourceThreadId(), artifact.area(), artifact.relativePath());
    }

    public void deleteThreadWorkspace(String userId, String threadId) {
        deletePath(threadRoot(userId, threadId));
    }

    public void deleteWorkspacePath(String userId, String workspaceId, WorkspaceArea area, String relativePath) {
        deletePath(resolveWorkspacePath(userId, workspaceId, area, relativePath));
    }

    private void deletePath(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Failed to delete path: " + current, exception);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete path tree", exception);
        }
    }
}
