package com.xg.platform.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryRunEventRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryTaskRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadRepository;
import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.workspace.ArtifactType;
import com.xg.platform.contracts.workspace.ArtifactVisibility;
import com.xg.platform.contracts.workspace.RegisterArtifactCommand;
import com.xg.platform.contracts.document.DocumentStatus;
import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.contracts.shared.task.TaskStatus;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.document.application.ChunkIndexStore;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.document.application.SemanticChunker;
import com.xg.platform.shared.runtime.async.RetryableTaskException;
import com.xg.platform.shared.runtime.async.TaskDispatchRequest;
import com.xg.platform.shared.runtime.async.TaskDispatcher;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.tooling.application.CliToolExecutor;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentIngestServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void workspaceSchedulingCreatesAndReusesSingleIngestTask() throws Exception {
        Harness harness = createHarness(0, 3);
        ArtifactRecord uploadArtifact = registerWorkspaceUpload(harness, "notes.txt", "hello world");

        var first = harness.service().scheduleIngestion(harness.userId(), harness.workspaceId(), null, uploadArtifact);
        var second = harness.service().scheduleIngestion(harness.userId(), harness.workspaceId(), null, uploadArtifact);

        assertThat(first.documentId()).isEqualTo(second.documentId());
        assertThat(first.ingestTaskId()).isEqualTo(second.ingestTaskId());
        assertThat(first.ingestTaskId()).isNotBlank();
        assertThat(harness.dispatcher().requests()).hasSize(1);
        assertThat(harness.taskRepository().listWorkspaceTasks(harness.userId(), harness.workspaceId(), TaskKind.INGEST))
                .singleElement()
                .extracting(task -> task.taskId())
                .isEqualTo(first.ingestTaskId());
    }

    @Test
    void processRetriesThenCompletesWithoutDuplicatingArtifacts() throws Exception {
        Harness harness = createHarness(1, 3);
        ArtifactRecord uploadArtifact = registerWorkspaceUpload(harness, "retry.txt", "retry me");

        var ticket = harness.service().scheduleIngestion(harness.userId(), harness.workspaceId(), null, uploadArtifact);
        TaskDispatchRequest request = harness.dispatcher().onlyRequest();

        assertThatThrownBy(() -> harness.service().process(request))
                .isInstanceOf(RetryableTaskException.class);

        var afterFirstFailure = harness.taskRepository().findTaskById(harness.userId(), ticket.ingestTaskId()).orElseThrow();
        assertThat(afterFirstFailure.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(afterFirstFailure.attemptCount()).isEqualTo(1);
        assertThat(afterFirstFailure.lastError()).contains("extract failed");
        assertThat(harness.documentStore().findById(harness.userId(), harness.workspaceId(), ticket.documentId()).status())
                .isEqualTo(DocumentStatus.UPLOADED);

        harness.service().process(request);

        var completed = harness.taskRepository().findTaskById(harness.userId(), ticket.ingestTaskId()).orElseThrow();
        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.attemptCount()).isEqualTo(1);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(harness.documentStore().findById(harness.userId(), harness.workspaceId(), ticket.documentId()).status())
                .isEqualTo(DocumentStatus.READY);
        assertThat(harness.artifactService().listArtifactsByWorkspace(harness.userId(), harness.workspaceId(), true))
                .hasSize(3);
    }

    @Test
    void processStopsRetryingAtConfiguredMaxAttempts() throws Exception {
        Harness harness = createHarness(3, 2);
        ArtifactRecord uploadArtifact = registerWorkspaceUpload(harness, "broken.txt", "still broken");

        var ticket = harness.service().scheduleIngestion(harness.userId(), harness.workspaceId(), null, uploadArtifact);
        TaskDispatchRequest request = harness.dispatcher().onlyRequest();

        assertThatThrownBy(() -> harness.service().process(request))
                .isInstanceOf(RetryableTaskException.class);

        harness.service().process(request);

        var failed = harness.taskRepository().findTaskById(harness.userId(), ticket.ingestTaskId()).orElseThrow();
        assertThat(failed.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(2);
        assertThat(failed.completedAt()).isNotNull();
        assertThat(harness.documentStore().findById(harness.userId(), harness.workspaceId(), ticket.documentId()).status())
                .isEqualTo(DocumentStatus.FAILED);
    }

    private Harness createHarness(int failingExtractCalls, int maxAttempts) {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadService threadRuntimeService = new ThreadService(new InMemoryThreadRepository());
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        DocumentStore documentStore = new DocumentStore(workspaceManager, threadRuntimeService, objectMapper);
        RecordingTaskDispatcher dispatcher = new RecordingTaskDispatcher();
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        DocumentIngestService service = new DocumentIngestService(
                documentStore,
                new ChunkIndexStore(workspaceManager, objectMapper),
                artifactService,
                workspaceManager,
                taskRepository,
                new InMemoryRunEventRepository(),
                threadRuntimeService,
                new FakeCliToolExecutor(objectMapper, failingExtractCalls),
                objectMapper,
                dispatcher,
                new SemanticChunker(),
                new DocumentIngestService.Settings(maxAttempts, 15)
        );
        return new Harness(
                "user-1",
                "workspace-1",
                service,
                taskRepository,
                dispatcher,
                documentStore,
                artifactService,
                workspaceManager
        );
    }

    private ArtifactRecord registerWorkspaceUpload(Harness harness, String fileName, String content) throws Exception {
        Path path = harness.workspaceManager().resolveWorkspacePath(
                harness.userId(),
                harness.workspaceId(),
                WorkspaceArea.UPLOADS,
                fileName
        );
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return harness.artifactService().register(new RegisterArtifactCommand(
                harness.userId(),
                harness.workspaceId(),
                null,
                fileName,
                ArtifactType.UPLOAD,
                ArtifactVisibility.USER_VISIBLE,
                WorkspaceArea.UPLOADS,
                fileName,
                "text/plain"
        ));
    }

    private record Harness(
            String userId,
            String workspaceId,
            DocumentIngestService service,
            InMemoryTaskRepository taskRepository,
            RecordingTaskDispatcher dispatcher,
            DocumentStore documentStore,
            ArtifactService artifactService,
            WorkspaceManager workspaceManager
    ) {
    }

    private static final class RecordingTaskDispatcher implements TaskDispatcher {

        private final List<TaskDispatchRequest> requests = new ArrayList<>();

        @Override
        public void dispatch(TaskDispatchRequest request) {
            requests.add(request);
        }

        private List<TaskDispatchRequest> requests() {
            return List.copyOf(requests);
        }

        private TaskDispatchRequest onlyRequest() {
            return requests.get(0);
        }
    }

    private static final class FakeCliToolExecutor extends CliToolExecutor {

        private int remainingFailures;

        private FakeCliToolExecutor(ObjectMapper objectMapper, int remainingFailures) {
            super(objectMapper, List.of("python"), Path.of("noop.py"), Duration.ofSeconds(1));
            this.remainingFailures = remainingFailures;
        }

        @Override
        public <T> T execute(String toolName, Object request, Class<T> responseType) {
            if ("extract_document".equals(toolName)) {
                if (remainingFailures > 0) {
                    remainingFailures--;
                    throw new IllegalStateException("extract failed");
                }
                return createExtractResponse(responseType, "Recovered text");
            }
            throw new IllegalArgumentException("Unsupported tool: " + toolName);
        }

        private <T> T createExtractResponse(Class<T> responseType, String fullText) {
            try {
                Class<?> pageType = Arrays.stream(responseType.getEnclosingClass().getDeclaredClasses())
                        .filter(candidate -> candidate.getSimpleName().equals("ExtractedPage"))
                        .findFirst()
                        .orElseThrow();
                Constructor<?> pageConstructor = pageType.getDeclaredConstructor(int.class, String.class);
                pageConstructor.setAccessible(true);
                Object page = pageConstructor.newInstance(1, fullText);

                Constructor<T> responseConstructor = responseType.getDeclaredConstructor(String.class, int.class, String.class, List.class);
                responseConstructor.setAccessible(true);
                return responseConstructor.newInstance("text", 1, fullText, List.of(page));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to construct fake extract response", exception);
            }
        }
    }
}
