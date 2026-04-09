package com.xg.platform.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.document.DocumentStatus;
import com.xg.platform.contracts.validation.PlatformIds;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.workspace.WorkspaceManager;

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
import java.util.function.UnaryOperator;

public class DocumentStore {

    private static final TypeReference<List<DocumentRecord>> DOCUMENT_LIST_TYPE = new TypeReference<>() {
    };
    private static final String DOCUMENT_INDEX_RELATIVE_PATH = "documents/documents.json";

    private final WorkspaceManager workspaceManager;
    private final ThreadRuntimeService threadRuntimeService;
    private final ObjectMapper objectMapper;

    public DocumentStore(WorkspaceManager workspaceManager,
                         ThreadRuntimeService threadRuntimeService,
                         ObjectMapper objectMapper) {
        this.workspaceManager = workspaceManager;
        this.threadRuntimeService = threadRuntimeService;
        this.objectMapper = objectMapper;
    }

    public synchronized DocumentRecord createUploaded(String userId,
                                                      String workspaceId,
                                                      String sourceThreadId,
                                                      String sourceArtifactId,
                                                      String name) {
        Instant now = Instant.now();
        DocumentRecord record = new DocumentRecord(
                UUID.randomUUID().toString(),
                PlatformIds.requireWorkspaceId(workspaceId),
                sourceThreadId == null || sourceThreadId.isBlank() ? null : PlatformIds.requireThreadId(sourceThreadId),
                PlatformIds.requireIdentifier(sourceArtifactId, "sourceArtifactId"),
                name,
                DocumentStatus.UPLOADED,
                null,
                null,
                now,
                now
        );
        List<DocumentRecord> records = new ArrayList<>(readDocuments(indexPath(userId, workspaceId)));
        records.add(record);
        writeDocuments(indexPath(userId, workspaceId), records);
        return record;
    }

    public synchronized DocumentRecord findBySourceArtifactId(String userId, String workspaceId, String sourceArtifactId) {
        return listDocumentsByWorkspace(userId, workspaceId).stream()
                .filter(record -> record.sourceArtifactId().equals(sourceArtifactId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Document not found for artifact: " + sourceArtifactId));
    }

    public synchronized DocumentRecord findById(String userId, String workspaceId, String documentId) {
        return listDocumentsByWorkspace(userId, workspaceId).stream()
                .filter(record -> record.documentId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Document not found: " + documentId));
    }

    public synchronized List<DocumentRecord> listDocuments(String userId, String threadId) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return listDocumentsByWorkspace(userId, workspaceId);
    }

    public synchronized List<DocumentRecord> listDocumentsByWorkspace(String userId, String workspaceId) {
        return readDocuments(indexPath(userId, workspaceId)).stream()
                .sorted(Comparator.comparing(DocumentRecord::updatedAt).reversed())
                .toList();
    }

    public synchronized DocumentRecord markIngesting(String userId, String workspaceId, String documentId) {
        return update(userId, workspaceId, documentId, current -> new DocumentRecord(
                current.documentId(),
                current.workspaceId(),
                current.sourceThreadId(),
                current.sourceArtifactId(),
                current.name(),
                DocumentStatus.INGESTING,
                current.primaryTextArtifactId(),
                current.chunkIndexArtifactId(),
                current.createdAt(),
                Instant.now()
        ));
    }

    public synchronized DocumentRecord markUploaded(String userId, String workspaceId, String documentId) {
        return update(userId, workspaceId, documentId, current -> new DocumentRecord(
                current.documentId(),
                current.workspaceId(),
                current.sourceThreadId(),
                current.sourceArtifactId(),
                current.name(),
                DocumentStatus.UPLOADED,
                current.primaryTextArtifactId(),
                current.chunkIndexArtifactId(),
                current.createdAt(),
                Instant.now()
        ));
    }

    public synchronized DocumentRecord markReady(String userId,
                                                 String workspaceId,
                                                 String documentId,
                                                 String primaryTextArtifactId,
                                                 String chunkIndexArtifactId) {
        return update(userId, workspaceId, documentId, current -> new DocumentRecord(
                current.documentId(),
                current.workspaceId(),
                current.sourceThreadId(),
                current.sourceArtifactId(),
                current.name(),
                DocumentStatus.READY,
                primaryTextArtifactId,
                chunkIndexArtifactId,
                current.createdAt(),
                Instant.now()
        ));
    }

    public synchronized DocumentRecord markFailed(String userId, String workspaceId, String documentId) {
        return update(userId, workspaceId, documentId, current -> new DocumentRecord(
                current.documentId(),
                current.workspaceId(),
                current.sourceThreadId(),
                current.sourceArtifactId(),
                current.name(),
                DocumentStatus.FAILED,
                current.primaryTextArtifactId(),
                current.chunkIndexArtifactId(),
                current.createdAt(),
                Instant.now()
        ));
    }

    public synchronized List<DocumentRecord> deleteBySourceThread(String userId, String workspaceId, String sourceThreadId) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        String safeThreadId = PlatformIds.requireThreadId(sourceThreadId);
        Path indexPath = indexPath(safeUserId, safeWorkspaceId);
        List<DocumentRecord> existing = new ArrayList<>(readDocuments(indexPath));
        List<DocumentRecord> deleted = existing.stream()
                .filter(record -> safeThreadId.equals(record.sourceThreadId()))
                .toList();
        if (deleted.isEmpty()) {
            return List.of();
        }
        List<DocumentRecord> retained = existing.stream()
                .filter(record -> !safeThreadId.equals(record.sourceThreadId()))
                .toList();
        writeDocuments(indexPath, retained);
        return deleted;
    }

    private DocumentRecord update(String userId, String workspaceId, String documentId, UnaryOperator<DocumentRecord> updater) {
        Path indexPath = indexPath(userId, workspaceId);
        List<DocumentRecord> records = new ArrayList<>(readDocuments(indexPath));
        for (int index = 0; index < records.size(); index++) {
            DocumentRecord current = records.get(index);
            if (current.documentId().equals(documentId)) {
                DocumentRecord updated = updater.apply(current);
                records.set(index, updated);
                writeDocuments(indexPath, records);
                return updated;
            }
        }
        throw new NoSuchElementException("Document not found: " + documentId);
    }

    private Path indexPath(String userId, String workspaceId) {
        return workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.WORKSPACE, DOCUMENT_INDEX_RELATIVE_PATH);
    }

    private List<DocumentRecord> readDocuments(Path path) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), DOCUMENT_LIST_TYPE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read document index", exception);
        }
    }

    private void writeDocuments(Path path, List<DocumentRecord> records) {
        Path tempFile = null;
        try {
            Files.createDirectories(path.getParent());
            tempFile = Files.createTempFile(path.getParent(), "documents-", ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), records);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to persist document index", exception);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
