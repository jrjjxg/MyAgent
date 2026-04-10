package com.xg.platform.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.application.ShortTermMemoryProjectionService;
import com.xg.platform.conversation.application.ConversationCommandService;
import com.xg.platform.conversation.application.ConversationRouterService;
import com.xg.platform.conversation.runtime.ConversationGraphNodes;
import com.xg.platform.research.application.ResearchWorkflowService;
import com.xg.platform.research.runtime.ResearchExecutionGraphNodeService;
import com.xg.platform.agent.core.research.execution.ResearchExecutionSupport;
import com.xg.platform.research.application.ResearchDraftScopingService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryLongTermMemoryRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryMessageRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryResearchDraftRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryResearchTaskSnapshotRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryRunEventRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryTaskRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadMemorySnapshotRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadRepository;
import com.xg.platform.contracts.workspace.ArtifactType;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.PostMessageRequest;
import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.document.application.ContextAssembler;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.memory.application.NoOpThreadMemoryViewCache;
import com.xg.platform.document.application.SemanticChunker;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.runtime.async.PlatformTaskProcessor;
import com.xg.platform.shared.runtime.async.TaskDispatchRequest;
import com.xg.platform.shared.runtime.async.TaskDispatcher;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.shared.runtime.graph.CheckpointConfiguration;
import com.xg.platform.shared.runtime.graph.PlatformGraphRunner;
import com.xg.platform.shared.runtime.graph.RunEventConsumerRegistry;
import com.xg.platform.tooling.application.McpServerRegistry;
import com.xg.platform.research.application.ResearchTaskExecutionService;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;
import com.xg.platform.tooling.domain.ToolGroup;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.xg.platform.conversation.runtime.ConversationGraphDefinition;
import com.xg.platform.research.runtime.ResearchGraphDefinition;

class AgentExecutionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void executesHandlerPersistsEventsAndCreatesOutputArtifact() {
        Harness harness = createHarness(tempDir);
        List<RunEvent> emitted = new ArrayList<>();

        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("Analyze this thread", InteractionMode.CHAT, "gemini"),
                emitted::add
        );
        List<MessageRecord> messages = harness.messageRepository().listMessages("user-1", harness.threadId());

        assertThat(emitted).extracting(RunEvent::eventType).containsExactly(
                "message.accepted",
                "run.started",
                "agent.selected",
                "route.selected",
                "message.delta",
                "message.completed",
                "run.completed"
        );
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).interactionMode()).isEqualTo(InteractionMode.CHAT);
        assertThat(messages.get(1).role().name()).isEqualTo("ASSISTANT");
        assertThat(messages.get(1).content()).isEqualTo("hello world");
        assertThat(harness.artifactService().listArtifacts("user-1", harness.threadId()))
                .extracting(artifact -> artifact.type())
                .doesNotContain(ArtifactType.REPORT);
    }

    @Test
    void streamsAgentStepEventsBeforeFinalAssistantMessage() {
        Harness harness = createHarness(tempDir, new AgentStepStubAgentTurnExecutionSupport(), new NoOpAgentToolService());
        List<RunEvent> emitted = new ArrayList<>();

        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("Analyze this thread", InteractionMode.CHAT, "gemini"),
                emitted::add
        );

        assertThat(emitted).extracting(RunEvent::eventType).containsSubsequence(
                "run.started",
                "agent.step.started",
                "agent.step.delta",
                "agent.step.completed",
                "message.delta",
                "message.completed",
                "run.completed"
        );
    }

    @Test
    void keepsIntermediateStepTextInExecutionEventsUntilFinalAnswerIsCommitted() {
        Harness harness = createHarness(
                tempDir,
                new StepThenToolThenFinalAgentTurnExecutionSupport(),
                new SearchToolAgentToolService()
        );
        List<RunEvent> emitted = new ArrayList<>();

        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("Analyze this thread", InteractionMode.CHAT, "gemini"),
                emitted::add
        );

        assertThat(emitted).extracting(RunEvent::eventType).containsSubsequence(
                "agent.step.started",
                "agent.step.delta",
                "agent.step.completed",
                "tool.started",
                "tool.completed",
                "agent.step.started",
                "agent.step.delta",
                "agent.step.completed",
                "message.delta",
                "message.completed",
                "run.completed"
        );
        List<MessageRecord> messages = harness.messageRepository().listMessages("user-1", harness.threadId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).contains("Final answer");
        assertThat(messages.get(1).content()).doesNotContain("Looking this up");
    }

    @Test
    void fallsBackToAssistantReplyWhenModelFailsAfterToolExecution() {
        Harness harness = createHarness(
                tempDir,
                new ToolThenFailAgentTurnExecutionSupport(),
                new SearchToolAgentToolService()
        );
        List<RunEvent> emitted = new ArrayList<>();

        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("Help me check tomorrow's Tianjin weather", InteractionMode.CHAT, "gemini"),
                emitted::add
        );

        List<MessageRecord> messages = harness.messageRepository().listMessages("user-1", harness.threadId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).contains("我已经完成了部分外部查询");
        assertThat(messages.get(1).content()).contains("## Sources");
        assertThat(emitted).extracting(RunEvent::eventType).contains("tool.completed", "run.completed");
        assertThat(emitted).extracting(RunEvent::eventType).doesNotContain("run.failed");
        assertThat(runCompletedPayload(emitted))
                .containsEntry("degraded", true)
                .containsEntry("degradationReasons", List.of("model-step-fallback"));
    }

    @Test
    void continuesTurnWhenRequestedToolCannotBeResolved() {
        Harness harness = createHarness(
                tempDir,
                new MissingToolThenRecoverAgentTurnExecutionSupport(),
                new SearchToolAgentToolService()
        );
        List<RunEvent> emitted = new ArrayList<>();

        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("please check this with a tool", InteractionMode.CHAT, "gemini"),
                emitted::add
        );

        List<MessageRecord> messages = harness.messageRepository().listMessages("user-1", harness.threadId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).contains("Recovered after tool failure");
        assertThat(emitted).extracting(RunEvent::eventType).contains("tool.failed", "run.completed");
        assertThat(emitted).extracting(RunEvent::eventType).doesNotContain("run.failed");
        assertThat(runCompletedPayload(emitted))
                .containsEntry("degraded", true)
                .containsEntry("degradationReasons", List.of("tool-failed:missing_tool"));
    }

    @Test
    void treatsToolErrorResultsAsFailuresAndMarksRunDegraded() {
        Harness harness = createHarness(
                tempDir,
                new ToolThenRecoverAgentTurnExecutionSupport("web_search"),
                new ErrorResultSearchToolService()
        );
        List<RunEvent> emitted = new ArrayList<>();

        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("please use search", InteractionMode.CHAT, "gemini"),
                emitted::add
        );

        List<MessageRecord> messages = harness.messageRepository().listMessages("user-1", harness.threadId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).contains("Recovered after tool failure");
        assertThat(emitted).extracting(RunEvent::eventType).contains("tool.failed", "run.completed");
        assertThat(emitted).extracting(RunEvent::eventType).doesNotContain("tool.completed");
        assertThat(toolFailedPayload(emitted)).containsEntry("error", "search backend unavailable");
        assertThat(runCompletedPayload(emitted))
                .containsEntry("degraded", true)
                .containsEntry("degradationReasons", List.of("tool-failed:web_search"));
    }

    @Test
    void timesOutSlowToolsAndMarksRunDegraded() {
        Harness harness = createHarness(
                tempDir,
                new ToolThenRecoverAgentTurnExecutionSupport("web_search"),
                new SlowSearchToolService()
        );
        List<RunEvent> emitted = new ArrayList<>();

        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("please use search", InteractionMode.CHAT, "gemini"),
                emitted::add
        );

        List<MessageRecord> messages = harness.messageRepository().listMessages("user-1", harness.threadId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).content()).contains("Recovered after tool failure");
        assertThat(emitted).extracting(RunEvent::eventType).contains("tool.failed", "run.completed");
        assertThat(emitted).extracting(RunEvent::eventType).doesNotContain("tool.completed");
        assertThat(((String) toolFailedPayload(emitted).get("error")).toLowerCase()).contains("timed out");
        assertThat(runCompletedPayload(emitted))
                .containsEntry("degraded", true)
                .containsEntry("degradationReasons", List.of("tool-failed:web_search"));
    }

    @Test
    void rejectsDeepResearchWhenTaskIsActiveButStillAllowsChat() {
        Harness harness = createHarness(tempDir);
        harness.taskRepository().createQueuedTask(
                "user-1",
                harness.threadId(),
                "task-1",
                "general-agent",
                TaskKind.RESEARCH,
                "AI chips",
                "Research AI chips",
                null
        );

        assertThatThrownBy(() -> harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("Start deep research", InteractionMode.DEEP_RESEARCH, "gemini"),
                event -> {
                }
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A research task is already running for this thread");

        List<RunEvent> emitted = new ArrayList<>();
        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("Still answer this chat", InteractionMode.CHAT, "gemini"),
                emitted::add
        );

        assertThat(emitted).extracting(RunEvent::eventType).contains("run.completed");
        assertThat(harness.messageRepository().listMessages("user-1", harness.threadId())).hasSize(2);
    }

    @Test
    void routesIngestTasksToDocumentIngestService() {
        Harness harness = createHarness(tempDir);
        TaskDispatchRequest request = TaskDispatchRequest.of(
                "user-1",
                harness.threadId(),
                "task-ingest",
                TaskKind.INGEST,
                null,
                "{\"documentId\":\"doc-1\"}"
        );

        harness.executionService().process(request);

        assertThat(harness.documentIngestService().lastProcessedRequest()).isEqualTo(request);
    }

    @Test
    void prepareMessageExecutionValidatesSelectedDocumentsAgainstWorkspace() {
        Harness harness = createHarness(tempDir);
        DocumentRecord document = harness.documentStore().createUploaded(
                "user-1",
                "workspace-1",
                harness.threadId(),
                "artifact-1",
                "stack-lstm.pdf"
        );

        harness.executionService().prepareMessageExecution(
                "user-1",
                harness.threadId(),
                new PostMessageRequest("Read this doc", InteractionMode.CHAT, "gemini", List.of(), List.of(document.documentId()))
        );

        assertThatThrownBy(() -> harness.executionService().prepareMessageExecution(
                "user-1",
                harness.threadId(),
                new PostMessageRequest("Read this doc", InteractionMode.CHAT, "gemini", List.of(), List.of("missing-doc"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown document for this workspace: missing-doc");
    }

    @Test
    void doesNotIssueStandaloneRouterModelCallsForChatTurns() {
        CountingRunTextTurnSupport support = new CountingRunTextTurnSupport();
        Harness harness = createHarness(tempDir, support, new NoOpAgentToolService());

        harness.executionService().executeMessage(
                "user-1",
                harness.threadId(),
                PostMessageRequest.of("Help me check tomorrow's Tianjin weather", InteractionMode.CHAT, "gemini"),
                event -> {
                }
        );

        assertThat(support.runTextTurnCalls()).isZero();
    }

    private static Harness createHarness(Path tempDir) {
        return createHarness(tempDir, new StubAgentTurnExecutionSupport(), new NoOpAgentToolService());
    }

    private static Harness createHarness(Path tempDir,
                                         AgentTurnExecutionSupport agentTurnExecutionSupport,
                                         AgentToolService agentToolService) {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadService threadRuntimeService = new ThreadService(new InMemoryThreadRepository());
        String threadId = threadRuntimeService.createThread("user-1", "workspace-1", "Thread").threadId();
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        DocumentStore documentStore = new DocumentStore(workspaceManager, threadRuntimeService, objectMapper);
        TaskRepository taskRepository = new InMemoryTaskRepository();
        RunEventRepository runEventRepository = new InMemoryRunEventRepository();
        MessageRepository messageRepository = new InMemoryMessageRepository();
        ResearchDraftRepository researchDraftRepository = new InMemoryResearchDraftRepository();
        var researchTaskSnapshotRepository = new InMemoryResearchTaskSnapshotRepository();
        var threadMemorySnapshotRepository = new InMemoryThreadMemorySnapshotRepository();
        var longTermMemoryRepository = new InMemoryLongTermMemoryRepository();
        var threadMemoryViewCache = new NoOpThreadMemoryViewCache();
        var shortTermMemoryProjectionService = ShortTermMemoryProjectionService.withDefaultCompressor(
                messageRepository,
                threadMemorySnapshotRepository,
                threadMemoryViewCache,
                researchDraftRepository,
                taskRepository,
                12
        );
        var conversationMemoryService = new ConversationMemoryService(
                threadRuntimeService,
                messageRepository,
                threadMemorySnapshotRepository,
                threadMemoryViewCache,
                shortTermMemoryProjectionService,
                12,
                true
        );
        var memoryContextFormatter = new MemoryContextFormatter();
        TaskDispatcher taskDispatcher = request -> {
        };
        var memoryEventPublisher = (com.xg.platform.memory.port.MemoryEventPublisher) payload -> {
        };
        ResearchDraftScopingService researchScopingFlowService = new ResearchDraftScopingService(
                threadRuntimeService,
                messageRepository,
                researchDraftRepository,
                runEventRepository,
                memoryEventPublisher,
                artifactService,
                agentTurnExecutionSupport,
                objectMapper,
                false
        );
        var runEventConsumerRegistry = new RunEventConsumerRegistry();
        RecordingDocumentIngestService documentIngestService = new RecordingDocumentIngestService(documentStore);
        var interactionGraphNodeService = new ConversationGraphNodes(
                conversationMemoryService,
                longTermMemoryRepository,
                memoryContextFormatter,
                researchDraftRepository,
                threadRuntimeService,
                threadMemorySnapshotRepository,
                messageRepository,
                runEventRepository,
                memoryEventPublisher,
                artifactService,
                workspaceManager,
                documentStore,
                new ContextAssembler(),
                documentIngestService,
                new ConversationRouterService(),
                request -> "You are a helpful agent.",
                agentTurnExecutionSupport,
                agentToolService,
                new SkillRegistry(
                        tempDir.resolve("skills"),
                        new McpServerRegistry(tempDir.resolve("extensions.json"), objectMapper)
                ),
                researchScopingFlowService,
                runEventConsumerRegistry,
                objectMapper,
                false,
                4,
                2,
                2,
                0,
                1_000
        );
        var researchExecutionGraphNodeService = new ResearchExecutionGraphNodeService(
                taskRepository,
                threadRuntimeService,
                conversationMemoryService,
                longTermMemoryRepository,
                memoryContextFormatter,
                new ResearchWorkflowService(
                        threadRuntimeService,
                        taskRepository,
                        runEventRepository,
                        messageRepository,
                        memoryEventPublisher,
                        artifactService,
                        workspaceManager,
                        new NoOpResearchExecutionSupport(),
                        agentTurnExecutionSupport,
                        null,
                        researchTaskSnapshotRepository,
                        objectMapper,
                        false,
                        8,
                        600_000L
                )
        );
        var checkpointConfiguration = new CheckpointConfiguration();
        ConversationCommandService conversationCommandService = new ConversationCommandService(
                threadRuntimeService,
                taskRepository,
                runEventRepository,
                messageRepository,
                workspaceManager,
                agentTurnExecutionSupport,
                documentIngestService,
                new PlatformGraphRunner(
                        ConversationGraphDefinition.compile(checkpointConfiguration, interactionGraphNodeService),
                        ResearchGraphDefinition.compile(checkpointConfiguration, researchExecutionGraphNodeService),
                        runEventConsumerRegistry
                ),
                false
        );
        PlatformTaskProcessor taskProcessor = new PlatformTaskProcessor(
                documentIngestService,
                new ResearchTaskExecutionService(new PlatformGraphRunner(
                        ConversationGraphDefinition.compile(checkpointConfiguration, interactionGraphNodeService),
                        ResearchGraphDefinition.compile(checkpointConfiguration, researchExecutionGraphNodeService),
                        runEventConsumerRegistry
                ))
        );
        return new Harness(threadId, messageRepository, taskRepository, artifactService, new ExecutionFacade(conversationCommandService, taskProcessor), documentIngestService, documentStore);
    }

    private record Harness(
            String threadId,
            MessageRepository messageRepository,
            TaskRepository taskRepository,
            ArtifactService artifactService,
            ExecutionFacade executionService,
            RecordingDocumentIngestService documentIngestService,
            DocumentStore documentStore
    ) {
    }

    private record ExecutionFacade(
            ConversationCommandService conversationCommandService,
            PlatformTaskProcessor taskProcessor
    ) {
        private void executeMessage(String userId, String threadId, PostMessageRequest request, java.util.function.Consumer<RunEvent> runEventConsumer) {
            conversationCommandService.executeMessage(userId, threadId, request, runEventConsumer);
        }

        private void prepareMessageExecution(String userId, String threadId, PostMessageRequest request) {
            conversationCommandService.prepareMessageExecution(userId, threadId, request);
        }

        private void process(TaskDispatchRequest request) {
            taskProcessor.process(request);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> runCompletedPayload(List<RunEvent> emitted) {
        return (Map<String, Object>) emitted.stream()
                .filter(event -> "run.completed".equals(event.eventType()))
                .reduce((first, second) -> second)
                .orElseThrow()
                .payload();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolFailedPayload(List<RunEvent> emitted) {
        return (Map<String, Object>) emitted.stream()
                .filter(event -> "tool.failed".equals(event.eventType()))
                .reduce((first, second) -> second)
                .orElseThrow()
                .payload();
    }

    private static final class RecordingDocumentIngestService extends DocumentIngestService {

        private final DocumentStore documentStore;
        private TaskDispatchRequest lastProcessedRequest;

        private RecordingDocumentIngestService(DocumentStore documentStore) {
            super(null, null, null, null, null, null, null, null, null, request -> {
            }, new SemanticChunker(), DocumentIngestService.Settings.defaults());
            this.documentStore = documentStore;
        }

        @Override
        public void process(TaskDispatchRequest request) {
            this.lastProcessedRequest = request;
        }

        @Override
        public List<DocumentRecord> listDocumentsByWorkspace(String userId, String workspaceId) {
            return documentStore.listDocumentsByWorkspace(userId, workspaceId);
        }

        private TaskDispatchRequest lastProcessedRequest() {
            return lastProcessedRequest;
        }
    }

    private static final class StubAgentTurnExecutionSupport implements AgentTurnExecutionSupport {
        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentModelStep runSingleStep(String providerId,
                                            AgentExecutionRequest request,
                                            List<AgentGraphMessage> messages,
                                            String currentUserGraphMessageId,
                                            String prompt,
                                            List<ToolDescriptor> availableTools) {
            return AgentModelStep.of("hello world", List.of());
        }
    }

    private static final class AgentStepStubAgentTurnExecutionSupport implements AgentTurnExecutionSupport {
        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentModelStep runSingleStep(String providerId,
                                            AgentExecutionRequest request,
                                            List<AgentGraphMessage> messages,
                                            String currentUserGraphMessageId,
                                            String prompt,
                                            List<ToolDescriptor> availableTools,
                                            AgentOutputEmitter outputEmitter) {
            outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_STARTED, Map.of("providerId", providerId));
            outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_DELTA, Map.of("delta", "thinking..."));
            outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_COMPLETED, Map.of("summary", "thinking...", "content", "thinking..."));
            return AgentModelStep.of("hello world", List.of());
        }
    }

    private static final class CountingRunTextTurnSupport implements AgentTurnExecutionSupport {

        private int runTextTurnCalls;

        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            runTextTurnCalls++;
            return "{}";
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentModelStep runSingleStep(String providerId,
                                            AgentExecutionRequest request,
                                            List<AgentGraphMessage> messages,
                                            String currentUserGraphMessageId,
                                            String prompt,
                                            List<ToolDescriptor> availableTools) {
            return AgentModelStep.of("hello world", List.of());
        }

        private int runTextTurnCalls() {
            return runTextTurnCalls;
        }
    }

    private static final class NoOpAgentToolService implements AgentToolService {
        @Override
        public List<ToolDescriptor> listAvailableTools(String userId) {
            return List.of();
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            ObjectNode output = JsonMapper.builder().build().createObjectNode();
            return new ToolExecutionResult(request.tool().name(), output, false, null);
        }
    }

    private static class SearchToolAgentToolService implements AgentToolService {

        private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        private final ToolDescriptor webSearch = new ToolDescriptor(
                "web_search",
                "Search the web",
                objectMapper.createObjectNode(),
                ToolGroup.SEARCH,
                "builtin"
        );

        @Override
        public List<ToolDescriptor> listAvailableTools(String userId) {
            return List.of(webSearch);
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            ObjectNode output = objectMapper.createObjectNode();
            output.putArray("results")
                    .add(objectMapper.createObjectNode()
                            .put("title", "Central Weather")
                            .put("url", "https://weather.example.com/tianjin"));
            return new ToolExecutionResult(request.tool().name(), output, false, "ok");
        }
    }

    private static final class ErrorResultSearchToolService extends SearchToolAgentToolService {
        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            ObjectNode output = JsonMapper.builder().build().createObjectNode();
            output.put("status", "error");
            output.put("error", "search backend unavailable");
            return new ToolExecutionResult(request.tool().name(), output, true, "search backend unavailable");
        }
    }

    private static final class SlowSearchToolService extends SearchToolAgentToolService {
        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            try {
                Thread.sleep(2_500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("sleep interrupted", exception);
            }
            return super.execute(request);
        }
    }

    private static final class ToolThenFailAgentTurnExecutionSupport implements AgentTurnExecutionSupport {

        private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentModelStep runSingleStep(String providerId,
                                            AgentExecutionRequest request,
                                            List<AgentGraphMessage> messages,
                                            String currentUserGraphMessageId,
                                            String prompt,
                                            List<ToolDescriptor> availableTools) {
            boolean hasToolResponse = messages.stream().anyMatch(message -> message.type() == AgentGraphMessageType.TOOL);
            if (!hasToolResponse) {
                return AgentModelStep.of(
                        "",
                        List.of(new AgentGraphToolCall(
                                "call-1",
                                "web_search",
                                objectMapper.createObjectNode().put("query", request.message())
                        ))
                );
            }
            throw new IllegalStateException("Simulated model failure");
        }
    }

    private static final class MissingToolThenRecoverAgentTurnExecutionSupport implements AgentTurnExecutionSupport {
        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentModelStep runSingleStep(String providerId,
                                            AgentExecutionRequest request,
                                            List<AgentGraphMessage> messages,
                                            String currentUserGraphMessageId,
                                            String prompt,
                                            List<ToolDescriptor> availableTools) {
            boolean hasToolResponse = messages.stream().anyMatch(message -> message.type() == AgentGraphMessageType.TOOL);
            if (!hasToolResponse) {
                return AgentModelStep.of(
                        "",
                        List.of(new AgentGraphToolCall(
                                "call-missing",
                                "missing_tool",
                                JsonNodeFactory.instance.objectNode()
                        ))
                );
            }
            return AgentModelStep.of("Recovered after tool failure", List.of());
        }
    }

    private static final class ToolThenRecoverAgentTurnExecutionSupport implements AgentTurnExecutionSupport {

        private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        private final String toolName;

        private ToolThenRecoverAgentTurnExecutionSupport(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentModelStep runSingleStep(String providerId,
                                            AgentExecutionRequest request,
                                            List<AgentGraphMessage> messages,
                                            String currentUserGraphMessageId,
                                            String prompt,
                                            List<ToolDescriptor> availableTools) {
            boolean hasToolResponse = messages.stream().anyMatch(message -> message.type() == AgentGraphMessageType.TOOL);
            if (!hasToolResponse) {
                return AgentModelStep.of(
                        "",
                        List.of(new AgentGraphToolCall(
                                "call-recover",
                                toolName,
                                objectMapper.createObjectNode().put("query", request.message())
                        ))
                );
            }
            return AgentModelStep.of("Recovered after tool failure", List.of());
        }
    }

    private static final class StepThenToolThenFinalAgentTurnExecutionSupport implements AgentTurnExecutionSupport {

        private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentModelStep runSingleStep(String providerId,
                                            AgentExecutionRequest request,
                                            List<AgentGraphMessage> messages,
                                            String currentUserGraphMessageId,
                                            String prompt,
                                            List<ToolDescriptor> availableTools,
                                            AgentOutputEmitter outputEmitter) {
            boolean hasToolResponse = messages.stream().anyMatch(message -> message.type() == AgentGraphMessageType.TOOL);
            if (!hasToolResponse) {
                outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_STARTED, Map.of("providerId", providerId));
                outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_DELTA, Map.of("delta", "Looking this up..."));
                outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_COMPLETED, Map.of("summary", "Looking this up...", "content", "Looking this up..."));
                return AgentModelStep.of(
                        "",
                        List.of(new AgentGraphToolCall(
                                "call-preview",
                                "web_search",
                                objectMapper.createObjectNode().put("query", request.message())
                        ))
                );
            }
            outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_STARTED, Map.of("providerId", providerId));
            outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_DELTA, Map.of("delta", "Final answer"));
            outputEmitter.emitEvent(com.xg.platform.contracts.shared.event.RunEventType.AGENT_STEP_COMPLETED, Map.of("summary", "Final answer", "content", "Final answer"));
            return AgentModelStep.of("Final answer", List.of());
        }
    }

    private static final class NoOpResearchExecutionSupport implements ResearchExecutionSupport {
        @Override
        public java.util.List<com.xg.platform.contracts.document.DocumentRecord> prepareResearchExecution(AgentExecutionRequest request, AgentOutputEmitter outputEmitter) {
            return java.util.List.of();
        }

        @Override
        public ResearchPlan createResearchPlan(com.xg.platform.contracts.research.ApprovedResearchPlan approvedPlan, java.util.List<com.xg.platform.contracts.document.DocumentRecord> documents) {
            return new ResearchPlan("summary", java.util.List.of());
        }

        @Override
        public ResearchUnitResult executeResearchUnit(String providerId, AgentExecutionRequest request, String researchBrief, java.util.List<String> refinementNotes, java.util.List<com.xg.platform.contracts.document.DocumentRecord> documents, ResearchUnit unit, AgentOutputEmitter outputEmitter, int stepIndex, int totalSteps) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<CompressedFinding> compressFindings(String providerId, AgentExecutionRequest request, String researchBrief, ResearchPlan researchPlan, java.util.List<ResearchUnitResult> unitResults) {
            return java.util.List.of();
        }

        @Override
        public String generateFinalReport(String providerId,
                                          AgentExecutionRequest request,
                                          String researchBrief,
                                          ResearchPlan researchPlan,
                                          java.util.List<CompressedFinding> findings,
                                          java.util.List<ReportCitation> citations,
                                          java.util.List<String> refinementNotes) {
            return "";
        }
    }
}

