package com.xg.platform.api.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.contracts.workspace.CreateUploadSessionRequest;
import com.xg.platform.contracts.workspace.ThreadRecord;
import com.xg.platform.contracts.workspace.ThreadStatus;
import com.xg.platform.workspace.port.ThreadRepository;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.port.WorkspaceRepository;
import com.xg.platform.workspace.application.WorkspaceService;
import com.xg.platform.contracts.workspace.WorkspaceRecord;
import com.xg.platform.contracts.workspace.WorkspaceStatus;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.UploadService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkedUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsSessionUploadsChunksAndCompletesArtifact() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadService threadRuntimeService = new ThreadService(new InMemoryThreadRepository());
        WorkspaceService workspaceRuntimeService = new WorkspaceService(new InMemoryWorkspaceRepository());
        String userId = "user-1";
        String threadId = threadRuntimeService.createThread(userId, "workspace-1", "Upload Thread").threadId();
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        UploadService uploadService = new UploadService(workspaceManager, artifactService, threadRuntimeService);
        DocumentIngestService documentIngestService = mock(DocumentIngestService.class);
        when(documentIngestService.scheduleIngestion(eq(userId), eq("workspace-1"), eq(threadId), any()))
                .thenReturn(new DocumentIngestService.DocumentIngestionTicket("doc-1", "ingest-1"));

        ChunkedUploadService service = new ChunkedUploadService(
                threadRuntimeService,
                workspaceRuntimeService,
                workspaceManager,
                uploadService,
                artifactService,
                documentIngestService,
                new InMemoryChunkUploadStateStore(),
                1024
        );

        var session = service.createSession(
                userId,
                threadId,
                new CreateUploadSessionRequest("notes.txt", "text/plain", 10L, 5)
        );
        assertThat(session.totalChunks()).isEqualTo(2);
        assertThat(session.uploadedChunks()).isZero();

        service.uploadChunk(userId, threadId, session.uploadId(), 0, "hello".getBytes(StandardCharsets.UTF_8));
        var status = service.uploadChunk(userId, threadId, session.uploadId(), 1, "world".getBytes(StandardCharsets.UTF_8));

        assertThat(status.uploadedChunks()).isEqualTo(2);
        assertThat(status.completed()).isFalse();

        var completed = service.completeSession(userId, threadId, session.uploadId());

        assertThat(completed.upload().documentId()).isEqualTo("doc-1");
        assertThat(completed.upload().ingestTaskId()).isEqualTo("ingest-1");
        assertThat(completed.upload().artifact().name()).isEqualTo("notes.txt");
        assertThat(completed.session().completed()).isTrue();
        assertThat(Files.readString(workspaceManager.resolveWorkspacePath(userId, "workspace-1", com.xg.platform.contracts.workspace.WorkspaceArea.UPLOADS, "notes.txt")))
                .isEqualTo("helloworld");
        verify(documentIngestService).scheduleIngestion(eq(userId), eq("workspace-1"), eq(threadId), any());
    }

    @Test
    void completesWorkspaceSessionAndReturnsIngestTaskId() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadService threadRuntimeService = new ThreadService(new InMemoryThreadRepository());
        WorkspaceService workspaceRuntimeService = new WorkspaceService(new InMemoryWorkspaceRepository());
        String userId = "user-1";
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        UploadService uploadService = new UploadService(workspaceManager, artifactService, threadRuntimeService);
        DocumentIngestService documentIngestService = mock(DocumentIngestService.class);
        when(documentIngestService.scheduleIngestion(eq(userId), eq("workspace-1"), eq((String) null), any()))
                .thenReturn(new DocumentIngestService.DocumentIngestionTicket("doc-workspace", "ingest-workspace"));

        ChunkedUploadService service = new ChunkedUploadService(
                threadRuntimeService,
                workspaceRuntimeService,
                workspaceManager,
                uploadService,
                artifactService,
                documentIngestService,
                new InMemoryChunkUploadStateStore(),
                1024
        );

        var session = service.createSession(
                userId,
                "workspace-1",
                null,
                new CreateUploadSessionRequest("workspace-notes.txt", "text/plain", 10L, 5)
        );

        service.uploadChunkByWorkspace(userId, "workspace-1", session.uploadId(), 0, "hello".getBytes(StandardCharsets.UTF_8));
        service.uploadChunkByWorkspace(userId, "workspace-1", session.uploadId(), 1, "world".getBytes(StandardCharsets.UTF_8));

        var completed = service.completeSessionByWorkspace(userId, "workspace-1", session.uploadId());

        assertThat(completed.upload().documentId()).isEqualTo("doc-workspace");
        assertThat(completed.upload().ingestTaskId()).isEqualTo("ingest-workspace");
        verify(documentIngestService).scheduleIngestion(eq(userId), eq("workspace-1"), eq((String) null), any());
    }

    private static final class InMemoryThreadRepository implements ThreadRepository {

        private final Map<String, ThreadRecord> threads = new ConcurrentHashMap<>();

        @Override
        public ThreadRecord createThread(String userId, String workspaceId, String title) {
            Instant now = Instant.now();
            ThreadRecord record = new ThreadRecord("thread-1", userId, workspaceId, title, ThreadStatus.IDLE, now, now);
            threads.put(record.threadId(), record);
            return record;
        }

        @Override
        public List<ThreadRecord> listThreads(String userId) {
            return threads.values().stream().filter(thread -> thread.userId().equals(userId)).toList();
        }

        @Override
        public List<ThreadRecord> listThreads(String userId, String workspaceId) {
            return threads.values().stream()
                    .filter(thread -> thread.userId().equals(userId) && thread.workspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public ThreadRecord getThread(String userId, String threadId) {
            ThreadRecord record = threads.get(threadId);
            if (record == null || !record.userId().equals(userId)) {
                throw new IllegalArgumentException("Thread not found: " + threadId);
            }
            return record;
        }

        @Override
        public ThreadRecord touchThread(String userId, String threadId) {
            ThreadRecord current = getThread(userId, threadId);
            ThreadRecord updated = new ThreadRecord(
                    current.threadId(),
                    current.userId(),
                    current.workspaceId(),
                    current.title(),
                    current.status(),
                    current.createdAt(),
                    Instant.now()
            );
            threads.put(threadId, updated);
            return updated;
        }

        @Override
        public void deleteThread(String userId, String threadId) {
            ThreadRecord current = threads.get(threadId);
            if (current != null && current.userId().equals(userId)) {
                threads.remove(threadId);
            }
        }
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {

        @Override
        public WorkspaceRecord createWorkspace(String userId, String title) {
            Instant now = Instant.now();
            return new WorkspaceRecord("workspace-1", userId, title, WorkspaceStatus.ACTIVE, now, now);
        }

        @Override
        public List<WorkspaceRecord> listWorkspaces(String userId) {
            return List.of(getWorkspace(userId, "workspace-1"));
        }

        @Override
        public WorkspaceRecord getWorkspace(String userId, String workspaceId) {
            Instant now = Instant.now();
            return new WorkspaceRecord(workspaceId, userId, "Workspace", WorkspaceStatus.ACTIVE, now, now);
        }

        @Override
        public WorkspaceRecord touchWorkspace(String userId, String workspaceId) {
            return getWorkspace(userId, workspaceId);
        }
    }
}
