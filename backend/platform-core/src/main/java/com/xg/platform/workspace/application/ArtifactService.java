package com.xg.platform.workspace.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.workspace.RegisterArtifactCommand;
import com.xg.platform.contracts.shared.validation.PlatformIds;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.workspace.application.ThreadService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Predicate;

public class ArtifactService {

    private static final TypeReference<List<ArtifactRecord>> ARTIFACT_LIST_TYPE = new TypeReference<>() {
    };

    private final WorkspaceManager workspaceManager;
    private final ThreadService threadRuntimeService;
    private final ObjectMapper objectMapper;

    public ArtifactService(WorkspaceManager workspaceManager,
                           ThreadService threadRuntimeService,
                           ObjectMapper objectMapper) {
        this.workspaceManager = workspaceManager;
        this.threadRuntimeService = threadRuntimeService;
        this.objectMapper = objectMapper;
    }

    public synchronized ArtifactRecord register(RegisterArtifactCommand command) {
        String userId = PlatformIds.requireUserId(command.userId());
        String workspaceId = PlatformIds.requireWorkspaceId(command.workspaceId());
        String sourceThreadId = command.sourceThreadId() == null || command.sourceThreadId().isBlank()
                ? null
                : PlatformIds.requireThreadId(command.sourceThreadId());
        Path artifactPath = sourceThreadId == null || command.area() == com.xg.platform.contracts.workspace.WorkspaceArea.UPLOADS
                ? workspaceManager.resolveWorkspacePath(userId, workspaceId, command.area(), command.relativePath())
                : workspaceManager.resolvePath(userId, sourceThreadId, command.area(), command.relativePath());
        if (!Files.exists(artifactPath)) {
            throw new NoSuchElementException("Artifact file does not exist: " + command.relativePath());
        }

        try {
            ArtifactRecord artifact = new ArtifactRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    workspaceId,
                    sourceThreadId,
                    command.name(),
                    command.type(),
                    command.visibility(),
                    command.area(),
                    command.relativePath(),
                    command.contentType(),
                    Files.size(artifactPath),
                    Instant.now()
            );
            List<ArtifactRecord> artifacts = new ArrayList<>(readArtifacts(indexPath(userId, workspaceId)));
            artifacts.add(artifact);
            writeArtifacts(indexPath(userId, workspaceId), artifacts);
            return artifact;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to register artifact", exception);
        }
    }

    public synchronized List<ArtifactRecord> listArtifacts(String userId, String threadId) {
        return listArtifacts(userId, threadId, true);
    }

    public synchronized List<ArtifactRecord> listArtifacts(String userId, String threadId, boolean includeInternal) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return listArtifactsByWorkspace(userId, workspaceId, includeInternal);
    }

    public synchronized List<ArtifactRecord> listArtifactsByWorkspace(String userId, String workspaceId, boolean includeInternal) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        return readArtifacts(indexPath(safeUserId, safeWorkspaceId)).stream()
                .filter(artifact -> includeInternal || artifact.visibility() != com.xg.platform.contracts.workspace.ArtifactVisibility.INTERNAL)
                .sorted(Comparator.comparing(ArtifactRecord::createdAt).reversed())
                .toList();
    }

    public synchronized ArtifactRecord findArtifact(String userId, String threadId, String artifactId) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return findArtifactByWorkspace(userId, workspaceId, artifactId);
    }

    public synchronized ArtifactRecord findArtifactByWorkspace(String userId, String workspaceId, String artifactId) {
        return listArtifactsByWorkspace(userId, workspaceId, true).stream()
                .filter(artifact -> artifact.artifactId().equals(PlatformIds.requireIdentifier(artifactId, "artifactId")))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Artifact not found: " + artifactId));
    }

    public synchronized java.util.Optional<ArtifactRecord> findArtifactByWorkspacePath(String userId,
                                                                                       String workspaceId,
                                                                                       WorkspaceArea area,
                                                                                       String relativePath) {
        String safeRelativePath = PlatformIds.requireRelativePath(relativePath, "relativePath").toString().replace('\\', '/');
        return readArtifacts(indexPath(PlatformIds.requireUserId(userId), PlatformIds.requireWorkspaceId(workspaceId))).stream()
                .filter(artifact -> artifact.area() == area)
                .filter(artifact -> safeRelativePath.equals(artifact.relativePath()))
                .max(Comparator.comparing(ArtifactRecord::createdAt));
    }

    public Path resolveArtifactPath(String userId, ArtifactRecord artifact) {
        return workspaceManager.resolveArtifactPath(userId, artifact);
    }

    public synchronized List<ArtifactRecord> deleteArtifacts(String userId,
                                                             String workspaceId,
                                                             Predicate<ArtifactRecord> predicate) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        Path indexPath = indexPath(safeUserId, safeWorkspaceId);
        List<ArtifactRecord> existing = new ArrayList<>(readArtifacts(indexPath));
        List<ArtifactRecord> deleted = new ArrayList<>();
        List<ArtifactRecord> retained = new ArrayList<>();
        for (ArtifactRecord artifact : existing) {
            if (predicate != null && predicate.test(artifact)) {
                deleted.add(artifact);
            } else {
                retained.add(artifact);
            }
        }
        for (ArtifactRecord artifact : deleted) {
            deleteArtifactFile(safeUserId, artifact);
        }
        writeArtifacts(indexPath, retained);
        return List.copyOf(deleted);
    }

    private Path indexPath(String userId, String workspaceId) {
        return workspaceManager.ensureWorkspace(userId, workspaceId).root().resolve("artifacts.json");
    }

    private List<ArtifactRecord> readArtifacts(Path indexPath) {
        if (!Files.exists(indexPath)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(indexPath.toFile(), ARTIFACT_LIST_TYPE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read artifacts index", exception);
        }
    }

    private void writeArtifacts(Path indexPath, List<ArtifactRecord> artifacts) {
        Path tempFile = null;
        try {
            Files.createDirectories(indexPath.getParent());
            tempFile = Files.createTempFile(indexPath.getParent(), "artifacts-", ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), artifacts);
            Files.move(tempFile, indexPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist artifacts index", exception);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void deleteArtifactFile(String userId, ArtifactRecord artifact) {
        try {
            Files.deleteIfExists(resolveArtifactPath(userId, artifact));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete artifact file", exception);
        }
    }
}
