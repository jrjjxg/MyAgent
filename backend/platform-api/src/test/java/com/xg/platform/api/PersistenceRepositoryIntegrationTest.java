package com.xg.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.workspace.ArtifactType;
import com.xg.platform.contracts.workspace.ArtifactVisibility;
import com.xg.platform.contracts.workspace.RegisterArtifactCommand;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.api.config.MybatisPersistenceConfig;
import com.xg.platform.api.config.PersistenceConfig;
import com.xg.platform.api.config.PlatformPropertiesConfig;
import com.xg.platform.contracts.memory.CreateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryStatus;
import com.xg.platform.contracts.memory.LongTermMemoryType;
import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;
import com.xg.platform.contracts.memory.MemoryExtractionJobStatus;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.contracts.memory.UpdateLongTermMemoryRequest;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.MessageRole;
import com.xg.platform.contracts.research.ResearchDraftRecord;
import com.xg.platform.contracts.research.ResearchDraftStatus;
import com.xg.platform.contracts.research.ResearchPlanStep;
import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.contracts.shared.task.TaskRecord;
import com.xg.platform.contracts.shared.task.TaskStatus;
import com.xg.platform.memory.application.LongTermMemoryKeyRegistry;
import com.xg.platform.memory.application.LongTermMemoryMaintenanceService;
import com.xg.platform.memory.port.LongTermMemoryJobRepository;
import com.xg.platform.memory.port.LongTermMemoryRepository;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;
import com.xg.platform.workspace.application.ThreadDeletionService;
import com.xg.platform.workspace.port.ThreadRepository;
import com.xg.platform.workspace.port.WorkspaceRepository;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.WorkspaceManager;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = PersistenceRepositoryIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnabledIfSystemProperty(named = "platform.test.postgres.enabled", matches = "true")
class PersistenceRepositoryIntegrationTest {

    private static final Path DATA_ROOT = createDataRoot();

    @Autowired
    private ThreadRepository threadRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ResearchDraftRepository researchDraftRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Autowired
    private ThreadMemorySnapshotRepository threadMemorySnapshotRepository;

    @Autowired
    private LongTermMemoryRepository longTermMemoryRepository;

    @Autowired
    private LongTermMemoryJobRepository longTermMemoryJobRepository;

    @Autowired
    private ThreadDeletionService threadDeletionService;

    @Autowired
    private WorkspaceManager workspaceManager;

    @Autowired
    private ArtifactService artifactService;

    @Autowired
    private DocumentStore documentStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", LocalPostgresIntegrationSupport::jdbcUrl);
        registry.add("spring.datasource.username", LocalPostgresIntegrationSupport::username);
        registry.add("spring.datasource.password", LocalPostgresIntegrationSupport::password);
        registry.add("platform.data-root", () -> normalize(DATA_ROOT));
    }

    @Test
    void researchDraftsRoundTripJsonUpsertAndClear() {
        String userId = nextId("user");
        String threadId = threadRepository.createThread(userId, createWorkspace(userId, "Draft Workspace"), "Draft Thread").threadId();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        String draftId = nextId("draft");

        ResearchDraftRecord initial = new ResearchDraftRecord(
                draftId,
                threadId,
                ResearchDraftStatus.COLLECTING,
                "AI chip market",
                "Study market context and vendor competition.",
                "Understand the AI accelerator market.",
                "Cover context, competition, and implications.",
                "Research report",
                List.of("Use public sources", "Include uploaded notes"),
                List.of("What is the market baseline?", "How do Nvidia and AMD compare?"),
                1,
                "",
                List.of(),
                false,
                "msg-user-1",
                null,
                createdAt,
                createdAt
        );

        researchDraftRepository.save(userId, initial);

        ResearchDraftRecord updated = new ResearchDraftRecord(
                draftId,
                threadId,
                ResearchDraftStatus.READY,
                "AI chip market - Nvidia vs AMD",
                "Focus on vendor competition.",
                "Compare leading vendors.",
                "Market baseline plus Nvidia and AMD.",
                "Research report",
                List.of("Use public sources", "Prefer recent evidence"),
                List.of("What is the baseline?", "How do Nvidia and AMD differ?"),
                2,
                "Focus the plan on Nvidia and AMD competition.",
                List.of(new ResearchPlanStep(
                        "step-1",
                        "Compare Nvidia and AMD",
                        "Compare strategy and positioning.",
                        "Nvidia AMD AI accelerators competition",
                        true,
                        true,
                        "Vendor competition"
                )),
                true,
                "msg-user-2",
                "msg-assistant-2",
                createdAt,
                createdAt.plusSeconds(120)
        );

        researchDraftRepository.save(userId, updated);

        ResearchDraftRecord stored = researchDraftRepository.findActiveDraft(userId, threadId).orElseThrow();
        assertThat(stored.status()).isEqualTo(ResearchDraftStatus.READY);
        assertThat(stored.constraints()).containsExactly("Use public sources", "Prefer recent evidence");
        assertThat(stored.questions()).containsExactly("What is the baseline?", "How do Nvidia and AMD differ?");
        assertThat(stored.planSummary()).contains("Nvidia and AMD");
        assertThat(stored.planSteps()).singleElement().extracting(ResearchPlanStep::title).isEqualTo("Compare Nvidia and AMD");
        assertThat(stored.ready()).isTrue();

        researchDraftRepository.clear(userId, threadId);

        assertThat(researchDraftRepository.findActiveDraft(userId, threadId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select status from research_drafts where draft_id = ?",
                String.class,
                draftId
        )).isEqualTo(ResearchDraftStatus.DISCARDED.name());

        Timestamp discardedUpdatedAt = jdbcTemplate.queryForObject(
                "select updated_at from research_drafts where draft_id = ?",
                Timestamp.class,
                draftId
        );
        assertThat(discardedUpdatedAt).isNotNull();
        assertThat(discardedUpdatedAt.toInstant()).isAfter(updated.updatedAt());
    }

    @Test
    void runEventsRoundTripPayloadAndPreserveQueryOrdering() {
        String userId = nextId("user");
        String threadId = threadRepository.createThread(userId, createWorkspace(userId, "Run Event Workspace"), "Run Event Thread").threadId();
        String runId = nextId("run");

        ObjectNode startedPayload = objectMapper.createObjectNode()
                .put("kind", "started")
                .put("providerId", "gemini");
        ObjectNode deltaPayload = objectMapper.createObjectNode()
                .put("kind", "delta")
                .put("text", "hello world");
        ObjectNode reportPayload = objectMapper.createObjectNode()
                .put("kind", "report")
                .put("artifactId", nextId("artifact"));

        runEventRepository.appendEvent(userId, threadId, new RunEvent(
                runId,
                threadId,
                "run.started",
                Instant.parse("2026-01-02T00:00:00Z"),
                startedPayload
        ));
        runEventRepository.appendEvent(userId, threadId, new RunEvent(
                runId,
                threadId,
                "message.delta",
                Instant.parse("2026-01-02T00:00:05Z"),
                deltaPayload
        ));
        runEventRepository.appendEvent(userId, threadId, new RunEvent(
                nextId("other-run"),
                threadId,
                "research.report.ready",
                Instant.parse("2026-01-02T00:00:10Z"),
                reportPayload
        ));

        List<RunEvent> runEvents = runEventRepository.listEvents(userId, threadId, runId);
        assertThat(runEvents).extracting(RunEvent::eventType)
                .containsExactly("run.started", "message.delta");
        assertThat((JsonNode) runEvents.get(0).payload()).isEqualTo(startedPayload);
        assertThat((JsonNode) runEvents.get(1).payload()).isEqualTo(deltaPayload);

        List<RunEvent> latestEvents = runEventRepository.listEvents(userId, threadId, 2);
        assertThat(latestEvents).extracting(RunEvent::eventType)
                .containsExactly("research.report.ready", "message.delta");

        List<RunEvent> delegatedLookup = runEventRepository.listEvents(userId, threadId, List.<TaskRecord>of(), runId, 1);
        assertThat(delegatedLookup).extracting(RunEvent::eventType)
                .containsExactly("run.started", "message.delta");
    }

    @Test
    void threadMemorySnapshotsUpsertInPlace() {
        String userId = nextId("user");
        String threadId = threadRepository.createThread(userId, createWorkspace(userId, "Memory Snapshot Workspace"), "Memory Snapshot Thread").threadId();
        Instant firstUpdatedAt = Instant.parse("2026-01-03T00:00:00Z");
        Instant secondUpdatedAt = Instant.parse("2026-01-03T00:10:00Z");

        threadMemorySnapshotRepository.save(userId, new ThreadMemorySnapshotRecord(
                threadId,
                userId,
                "First summary",
                "msg-1",
                List.of(new MessageRecord(
                        "msg-pending-1",
                        threadId,
                        MessageRole.USER,
                        "Pending bridge message",
                        InteractionMode.CHAT,
                        "run-memory-1",
                        null,
                        Instant.parse("2026-01-03T00:00:01Z")
                )),
                "msg-2",
                20,
                "draft-1",
                "task-1",
                "queued",
                List.of("weather"),
                firstUpdatedAt
        ));

        threadMemorySnapshotRepository.save(userId, new ThreadMemorySnapshotRecord(
                threadId,
                userId,
                "Updated summary",
                "msg-2",
                List.of(new MessageRecord(
                        "msg-pending-2",
                        threadId,
                        MessageRole.ASSISTANT,
                        "Second pending bridge message",
                        InteractionMode.CHAT,
                        "run-memory-2",
                        null,
                        Instant.parse("2026-01-03T00:10:01Z")
                )),
                "msg-3",
                24,
                "draft-2",
                "task-2",
                "running",
                List.of("weather", "stock-monitor"),
                secondUpdatedAt
        ));

        ThreadMemorySnapshotRecord stored = threadMemorySnapshotRepository.findByThread(userId, threadId).orElseThrow();
        assertThat(stored.summary()).isEqualTo("Updated summary");
        assertThat(stored.lastCompactedMessageId()).isEqualTo("msg-2");
        assertThat(stored.pendingHistoricalMessages()).singleElement()
                .extracting(MessageRecord::messageId)
                .isEqualTo("msg-pending-2");
        assertThat(stored.recentEndMessageId()).isEqualTo("msg-3");
        assertThat(stored.recentWindowSize()).isEqualTo(24);
        assertThat(stored.activeDraftId()).isEqualTo("draft-2");
        assertThat(stored.activeTaskId()).isEqualTo("task-2");
        assertThat(stored.taskStage()).isEqualTo("running");
        assertThat(stored.activeSkillIds()).containsExactly("weather", "stock-monitor");
        assertThat(stored.updatedAt()).isEqualTo(secondUpdatedAt);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from thread_memory_snapshots where thread_id = ?",
                Integer.class,
                threadId
        )).isEqualTo(1);
    }

    @Test
    void memoryExtractionJobsHonorConflictHandlingAndTransitions() {
        String userId = nextId("user");
        String threadId = threadRepository.createThread(userId, createWorkspace(userId, "Memory Jobs Workspace"), "Memory Jobs Thread").threadId();
        String extractorVersion = "v1";
        String messageId = nextId("message");

        MemoryExtractionJobRecord queued = longTermMemoryJobRepository.createQueuedIfAbsent(
                userId,
                threadId,
                messageId,
                extractorVersion,
                5
        ).orElseThrow();

        assertThat(queued.status()).isEqualTo(MemoryExtractionJobStatus.QUEUED);
        assertThat(queued.attemptCount()).isZero();
        assertThat(queued.eligibleTurnCount()).isEqualTo(5);

        assertThat(longTermMemoryJobRepository.createQueuedIfAbsent(userId, threadId, messageId, extractorVersion, 5)).isEmpty();

        MemoryExtractionJobRecord running = longTermMemoryJobRepository.markRunning(queued.jobId()).orElseThrow();
        assertThat(running.status()).isEqualTo(MemoryExtractionJobStatus.RUNNING);
        assertThat(running.startedAt()).isNotNull();

        assertThat(longTermMemoryJobRepository.markRunning(queued.jobId())).isEmpty();

        MemoryExtractionJobRecord retried = longTermMemoryJobRepository.markFailure(queued.jobId(), "temporary", false);
        assertThat(retried.status()).isEqualTo(MemoryExtractionJobStatus.QUEUED);
        assertThat(retried.attemptCount()).isEqualTo(1);
        assertThat(retried.lastError()).isEqualTo("temporary");
        assertThat(retried.startedAt()).isNull();
        assertThat(retried.completedAt()).isNull();

        assertThat(longTermMemoryJobRepository.markRunning(queued.jobId())).isPresent();

        MemoryExtractionJobRecord failed = longTermMemoryJobRepository.markFailure(queued.jobId(), "fatal", true);
        assertThat(failed.status()).isEqualTo(MemoryExtractionJobStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(2);
        assertThat(failed.lastError()).isEqualTo("fatal");
        assertThat(failed.completedAt()).isNotNull();

        MemoryExtractionJobRecord secondQueued = longTermMemoryJobRepository.createQueuedIfAbsent(
                userId,
                threadId,
                nextId("message"),
                extractorVersion,
                3
        ).orElseThrow();
        assertThat(longTermMemoryJobRepository.markRunning(secondQueued.jobId())).isPresent();

        MemoryExtractionJobRecord succeeded = longTermMemoryJobRepository.markSucceeded(secondQueued.jobId());
        assertThat(succeeded.status()).isEqualTo(MemoryExtractionJobStatus.SUCCEEDED);
        assertThat(succeeded.lastError()).isNull();
        assertThat(succeeded.completedAt()).isNotNull();
    }

    @Test
    void messagesAndTasksPreserveCrudAndOrdering() {
        String userId = nextId("user");
        String threadId = threadRepository.createThread(userId, createWorkspace(userId, "Messages and Tasks Workspace"), "Messages and Tasks Thread").threadId();

        MessageRecord firstMessage = new MessageRecord(
                nextId("message"),
                threadId,
                MessageRole.USER,
                "hello",
                InteractionMode.CHAT,
                nextId("run"),
                null,
                Instant.parse("2026-01-04T00:00:00Z")
        );
        MessageRecord secondMessage = new MessageRecord(
                nextId("message"),
                threadId,
                MessageRole.ASSISTANT,
                "world",
                InteractionMode.CHAT,
                nextId("run"),
                null,
                Instant.parse("2026-01-04T00:00:01Z")
        );

        messageRepository.append(userId, firstMessage);
        messageRepository.append(userId, secondMessage);

        assertThat(messageRepository.listMessages(userId, threadId))
                .extracting(MessageRecord::messageId)
                .containsExactly(firstMessage.messageId(), secondMessage.messageId());
        assertThat(messageRepository.findLatestMessageId(userId, threadId)).contains(secondMessage.messageId());
        assertThat(messageRepository.findById(userId, threadId, firstMessage.messageId())).contains(firstMessage);

        TaskRecord firstTask = taskRepository.createQueuedTask(
                userId,
                threadId,
                nextId("task"),
                "agent-a",
                TaskKind.RESEARCH,
                "Initial research task",
                "queued",
                "draft-a"
        );
        pauseForOrdering();

        TaskRecord secondTask = taskRepository.createQueuedTask(
                userId,
                threadId,
                nextId("task"),
                "agent-b",
                TaskKind.INGEST,
                "Ingest task",
                "queued",
                null
        );
        pauseForOrdering();

        TaskRecord updatedFirstTask = taskRepository.updateTask(
                userId,
                threadId,
                firstTask.taskId(),
                TaskStatus.RUNNING,
                null,
                "processing",
                "running",
                55,
                "draft-b",
                null
        );

        TaskRecord storedTask = taskRepository.findTask(userId, threadId, firstTask.taskId()).orElseThrow();
        assertThat(storedTask)
                .usingRecursiveComparison()
                .ignoringFields("updatedAt")
                .isEqualTo(updatedFirstTask);
        assertThat(storedTask.updatedAt()).isAfterOrEqualTo(updatedFirstTask.createdAt());
        assertThat(taskRepository.listTasks(userId, threadId))
                .extracting(TaskRecord::taskId)
                .containsExactly(firstTask.taskId(), secondTask.taskId());
    }

    @Test
    void workspaceIngestTasksSupportDedupClaimRetryAndCompletion() {
        String userId = nextId("user");
        String workspaceId = createWorkspace(userId, "Workspace Ingest Tasks");

        TaskRecord created = taskRepository.createQueuedIngestTaskIfAbsent(
                userId,
                workspaceId,
                null,
                "document-1",
                nextId("task"),
                "notes.txt",
                "Queued document ingestion",
                2
        ).orElseThrow();

        assertThat(taskRepository.createQueuedIngestTaskIfAbsent(
                userId,
                workspaceId,
                null,
                "document-1",
                nextId("task"),
                "notes.txt",
                "Queued document ingestion",
                2
        )).isEmpty();

        assertThat(taskRepository.findIngestTaskByDocument(userId, workspaceId, "document-1"))
                .contains(created);
        assertThat(taskRepository.listWorkspaceTasks(userId, workspaceId, TaskKind.INGEST))
                .extracting(TaskRecord::taskId)
                .containsExactly(created.taskId());

        TaskRecord claimed = taskRepository.claimQueuedOrStaleRunningTask(
                userId,
                created.taskId(),
                Instant.now(),
                "Ingesting document",
                "ingest",
                50
        ).orElseThrow();
        assertThat(claimed.status()).isEqualTo(TaskStatus.RUNNING);
        assertThat(claimed.startedAt()).isNotNull();
        assertThat(taskRepository.claimQueuedOrStaleRunningTask(
                userId,
                created.taskId(),
                Instant.now().minusSeconds(60),
                "duplicate",
                "ingest",
                50
        )).isEmpty();

        TaskRecord retried = taskRepository.requeueTask(
                userId,
                created.taskId(),
                "Retrying document ingestion",
                "retrying",
                0,
                "temporary"
        );
        assertThat(retried.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(retried.attemptCount()).isEqualTo(1);
        assertThat(retried.lastError()).isEqualTo("temporary");
        assertThat(retried.startedAt()).isNull();

        TaskRecord reset = taskRepository.resetTaskToQueued(
                userId,
                created.taskId(),
                "Queued document ingestion",
                "queued",
                0
        );
        assertThat(reset.status()).isEqualTo(TaskStatus.QUEUED);
        assertThat(reset.attemptCount()).isEqualTo(1);
        assertThat(reset.lastError()).isNull();

        assertThat(taskRepository.claimQueuedOrStaleRunningTask(
                userId,
                created.taskId(),
                Instant.now(),
                "Ingesting document",
                "ingest",
                60
        )).isPresent();

        TaskRecord completed = taskRepository.markCompleted(userId, created.taskId(), "Document ingested", null);
        assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();

        TaskRecord second = taskRepository.createQueuedIngestTaskIfAbsent(
                userId,
                workspaceId,
                null,
                "document-2",
                nextId("task"),
                "broken.txt",
                "Queued document ingestion",
                2
        ).orElseThrow();
        assertThat(taskRepository.claimQueuedOrStaleRunningTask(
                userId,
                second.taskId(),
                Instant.now(),
                "Ingesting document",
                "ingest",
                50
        )).isPresent();

        TaskRecord failed = taskRepository.markFailed(
                userId,
                second.taskId(),
                "extract failed",
                "failed",
                50,
                "extract failed",
                true
        );
        assertThat(failed.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(1);
        assertThat(failed.completedAt()).isNotNull();
    }

    @Test
    void longTermMemoryUpsertsCanonicalKeysPreservesValueJsonAndSoftDelete() {
        String userId = nextId("user");
        String threadId = threadRepository.createThread(userId, createWorkspace(userId, "Long Term Memory Workspace"), "Long Term Memory Thread").threadId();
        ObjectNode firstValue = objectMapper.createObjectNode().put("style", "concise");
        ObjectNode updatedValue = objectMapper.createObjectNode().put("style", "concise-first");
        ObjectNode procedureValue = objectMapper.createObjectNode().put("answerStyle", "lead-with-conclusion");

        LongTermMemoryRecord first = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.PROFILE,
                LongTermMemoryKeyRegistry.PROFILE_OUTPUT_STYLE,
                "  Output Style  ",
                "  Use concise bullet summaries  ",
                firstValue,
                threadId,
                "message-a",
                "task-a"
        ));
        pauseForOrdering();

        LongTermMemoryRecord upsertedFirst = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.PROFILE,
                "output.style",
                "Output Style",
                "Updated concise style",
                updatedValue,
                threadId,
                "message-b",
                "task-b"
        ));
        pauseForOrdering();

        LongTermMemoryRecord semantic = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.SEMANTIC,
                "stock.monitoring.project",
                "Stock Monitoring Project",
                "Maintains a stock monitoring system project.",
                null,
                threadId,
                "message-c",
                "task-c"
        ));
        pauseForOrdering();

        LongTermMemoryRecord procedural = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.PROCEDURAL,
                "answer.style",
                "Answer style",
                "Lead with the conclusion and keep implementation notes short.",
                procedureValue,
                threadId,
                "message-d",
                "task-d"
        ));

        assertThat(upsertedFirst.memoryId()).isEqualTo(first.memoryId());
        assertThat(longTermMemoryRepository.findActiveByCanonicalKey(
                userId,
                LongTermMemoryType.PROFILE,
                LongTermMemoryKeyRegistry.PROFILE_OUTPUT_STYLE
        ))
                .map(LongTermMemoryRecord::memoryId)
                .contains(first.memoryId());
        assertThat(longTermMemoryRepository.findActiveByCanonicalKey(
                userId,
                LongTermMemoryType.PROCEDURAL,
                LongTermMemoryKeyRegistry.PROCEDURE_ANSWER_STYLE
        ))
                .map(LongTermMemoryRecord::memoryId)
                .contains(procedural.memoryId());

        LongTermMemoryRecord updatedFirst = longTermMemoryRepository.update(userId, first.memoryId(), new UpdateLongTermMemoryRequest(
                LongTermMemoryType.PROFILE,
                LongTermMemoryKeyRegistry.PROFILE_OUTPUT_STYLE,
                "   ",
                "  Final concise style  ",
                updatedValue,
                threadId,
                "message-e",
                "task-e"
        ));

        assertThat(updatedFirst.memoryType()).isEqualTo(LongTermMemoryType.PROFILE);
        assertThat(updatedFirst.canonicalKey()).isEqualTo(LongTermMemoryKeyRegistry.PROFILE_OUTPUT_STYLE);
        assertThat(updatedFirst.title()).isEqualTo("Output Style");
        assertThat(updatedFirst.content()).isEqualTo("Final concise style");
        assertThat(updatedFirst.valueJson()).isEqualTo(updatedValue);
        assertThat(updatedFirst.sourceMessageId()).isEqualTo("message-e");
        assertThat(updatedFirst.sourceTaskId()).isEqualTo("task-e");
        assertThat(semantic.canonicalKey()).isEqualTo("semantic.project.stock_monitoring");
        assertThat(procedural.valueJson()).isEqualTo(procedureValue);

        assertThat(longTermMemoryRepository.listActive(userId))
                .extracting(LongTermMemoryRecord::memoryId)
                .containsExactly(updatedFirst.memoryId(), procedural.memoryId(), semantic.memoryId());

        longTermMemoryRepository.delete(userId, first.memoryId());

        Optional<LongTermMemoryRecord> deleted = longTermMemoryRepository.findById(userId, first.memoryId());
        assertThat(deleted).isPresent();
        assertThat(deleted.orElseThrow().status()).isEqualTo(LongTermMemoryStatus.DELETED);
        assertThat(longTermMemoryRepository.listActive(userId))
                .extracting(LongTermMemoryRecord::memoryId)
                .containsExactly(procedural.memoryId(), semantic.memoryId());
    }

    @Test
    void episodicLongTermMemoryUsesSourceMessageIdAsWriteIdentity() {
        String userId = nextId("user");
        String threadId = threadRepository.createThread(userId, createWorkspace(userId, "Episodic Memory Workspace"), "Episodic Memory Thread").threadId();

        LongTermMemoryRecord first = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.EPISODIC,
                "paper.analysis",
                "Paper analysis",
                "Analyzed the stacked LSTM paper.",
                null,
                threadId,
                "message-episode-1",
                null
        ));

        LongTermMemoryRecord second = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.EPISODIC,
                "paper.review",
                "Paper review",
                "Updated the paper analysis summary.",
                null,
                threadId,
                "message-episode-1",
                null
        ));

        assertThat(second.memoryId()).isEqualTo(first.memoryId());
        assertThat(second.sourceMessageId()).isEqualTo("message-episode-1");
        assertThat(second.canonicalKey()).isEqualTo("episode.paper.review.message.episode.1");
        assertThat(longTermMemoryRepository.listActive(userId))
                .extracting(LongTermMemoryRecord::memoryId)
                .containsExactly(second.memoryId());
    }

    @Test
    void longTermMemoryCleanupRewritesAliasesAndDeletesOlderDuplicates() {
        String userId = nextId("user");
        Instant older = Instant.parse("2026-01-04T00:00:00Z");
        Instant newer = Instant.parse("2026-01-04T00:05:00Z");
        String olderId = nextId("memory");
        String newerId = nextId("memory");

        jdbcTemplate.update(
                """
                        insert into long_term_memory (
                            memory_id, user_id, memory_type, canonical_key, title, content, status, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                olderId,
                userId,
                LongTermMemoryType.SEMANTIC.name(),
                "research.context",
                "Research Context",
                "Old research context",
                LongTermMemoryStatus.ACTIVE.name(),
                Timestamp.from(older),
                Timestamp.from(older)
        );
        jdbcTemplate.update(
                """
                        insert into long_term_memory (
                            memory_id, user_id, memory_type, canonical_key, title, content, status, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                newerId,
                userId,
                LongTermMemoryType.SEMANTIC.name(),
                "research.topic",
                "Research Topic",
                "New research topic",
                LongTermMemoryStatus.ACTIVE.name(),
                Timestamp.from(newer),
                Timestamp.from(newer)
        );

        LongTermMemoryMaintenanceService.CleanupResult result =
                new LongTermMemoryMaintenanceService(longTermMemoryRepository).cleanupUserMemories(userId);

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.rewritten()).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(longTermMemoryRepository.listActive(userId))
                .singleElement()
                .satisfies(memory -> {
                    assertThat(memory.memoryId()).isEqualTo(newerId);
                    assertThat(memory.canonicalKey()).isEqualTo("semantic.research_topic.primary");
                    assertThat(memory.content()).isEqualTo("New research topic");
                });
        assertThat(longTermMemoryRepository.findById(userId, olderId))
                .map(LongTermMemoryRecord::status)
                .contains(LongTermMemoryStatus.DELETED);
    }

    @Test
    void deletingThreadCascadesLongTermMemoryAndThreadScopedState() throws Exception {
        String userId = nextId("user");
        String workspaceId = createWorkspace(userId, "Thread Cleanup Workspace");
        String threadId = threadRepository.createThread(userId, workspaceId, "Cleanup Thread").threadId();
        String survivorThreadId = threadRepository.createThread(userId, workspaceId, "Survivor Thread").threadId();

        messageRepository.append(userId, new MessageRecord(
                nextId("message"),
                threadId,
                MessageRole.USER,
                "cleanup me",
                InteractionMode.CHAT,
                nextId("run"),
                null,
                Instant.parse("2026-01-05T00:00:00Z")
        ));
        researchDraftRepository.save(userId, new ResearchDraftRecord(
                nextId("draft"),
                threadId,
                ResearchDraftStatus.READY,
                "Cleanup draft",
                "Cleanup brief",
                "",
                "",
                "",
                List.of(),
                List.of(),
                1,
                "",
                List.of(),
                true,
                null,
                null,
                Instant.parse("2026-01-05T00:00:00Z"),
                Instant.parse("2026-01-05T00:00:00Z")
        ));
        taskRepository.createQueuedTask(
                userId,
                threadId,
                nextId("task"),
                "general-agent",
                TaskKind.RESEARCH,
                "Cleanup task",
                "queued",
                null
        );
        runEventRepository.appendEvent(userId, threadId, new RunEvent(
                nextId("run"),
                threadId,
                "run.started",
                Instant.parse("2026-01-05T00:00:01Z"),
                objectMapper.createObjectNode().put("kind", "cleanup")
        ));
        threadMemorySnapshotRepository.save(userId, ThreadMemorySnapshotRecord.withoutActiveSkillIds(
                threadId,
                userId,
                "Cleanup summary",
                "message-1",
                List.of(),
                "message-2",
                20,
                null,
                null,
                null,
                Instant.parse("2026-01-05T00:00:02Z")
        ));
        longTermMemoryJobRepository.createQueuedIfAbsent(userId, threadId, nextId("message"), "v1", 2);

        LongTermMemoryRecord threadMemory = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.SEMANTIC,
                "semantic.cleanup.thread",
                "Cleanup memory",
                "Belongs to cleanup thread",
                null,
                threadId,
                "message-cleanup",
                null
        ));
        LongTermMemoryRecord survivorMemory = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.SEMANTIC,
                "semantic.cleanup.survivor",
                "Survivor memory",
                "Belongs to survivor thread",
                null,
                survivorThreadId,
                "message-survivor",
                null
        ));

        Path uploadPath = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.UPLOADS, "cleanup-thread.txt");
        Files.createDirectories(uploadPath.getParent());
        Files.writeString(uploadPath, "cleanup");
        ArtifactRecord uploadArtifact = artifactService.register(new RegisterArtifactCommand(
                userId,
                workspaceId,
                threadId,
                "cleanup-thread.txt",
                ArtifactType.UPLOAD,
                ArtifactVisibility.USER_VISIBLE,
                WorkspaceArea.UPLOADS,
                "cleanup-thread.txt",
                "text/plain"
        ));
        DocumentRecord document = documentStore.createUploaded(userId, workspaceId, threadId, uploadArtifact.artifactId(), "cleanup-thread.txt");
        Files.createDirectories(workspaceManager.resolveWorkspacePath(
                userId,
                workspaceId,
                WorkspaceArea.WORKSPACE,
                "documents/" + document.documentId()
        ));

        threadDeletionService.deleteThread(userId, threadId);

        assertThat(threadRepository.listThreads(userId))
                .extracting(com.xg.platform.contracts.workspace.ThreadRecord::threadId)
                .containsExactly(survivorThreadId);
        assertThat(messageRepository.listMessages(userId, threadId)).isEmpty();
        assertThat(researchDraftRepository.findActiveDraft(userId, threadId)).isEmpty();
        assertThat(taskRepository.listTasks(userId, threadId)).isEmpty();
        assertThat(runEventRepository.listEvents(userId, threadId, 10)).isEmpty();
        assertThat(threadMemorySnapshotRepository.findByThread(userId, threadId)).isEmpty();
        assertThat(longTermMemoryJobRepository.hasPendingJob(userId, threadId, "v1")).isFalse();
        assertThat(longTermMemoryRepository.findById(userId, threadMemory.memoryId()))
                .map(LongTermMemoryRecord::status)
                .contains(LongTermMemoryStatus.DELETED);
        assertThat(longTermMemoryRepository.findById(userId, survivorMemory.memoryId()))
                .map(LongTermMemoryRecord::status)
                .contains(LongTermMemoryStatus.ACTIVE);
        assertThat(documentStore.listDocumentsByWorkspace(userId, workspaceId))
                .extracting(DocumentRecord::documentId)
                .doesNotContain(document.documentId());
        assertThat(artifactService.listArtifactsByWorkspace(userId, workspaceId, true))
                .extracting(ArtifactRecord::artifactId)
                .doesNotContain(uploadArtifact.artifactId());
        assertThat(Files.exists(workspaceManager.threadRoot(userId, threadId))).isFalse();
        assertThat(Files.exists(workspaceManager.resolveWorkspacePath(
                userId,
                workspaceId,
                WorkspaceArea.WORKSPACE,
                "documents/" + document.documentId()
        ))).isFalse();
    }

    private static String nextId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String createWorkspace(String userId, String title) {
        return workspaceRepository.createWorkspace(userId, title).workspaceId();
    }

    private static void pauseForOrdering() {
        try {
            Thread.sleep(10L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for timestamp ordering", exception);
        }
    }

    private static Path createDataRoot() {
        try {
            Path path = Path.of("target", "persistence-tests", UUID.randomUUID().toString());
            Files.createDirectories(path);
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create persistence test data root", exception);
        }
    }

    private static String normalize(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({
            PlatformPropertiesConfig.class,
            MybatisPersistenceConfig.class,
            PersistenceConfig.class
    })
    static class TestApplication {
    }
}
