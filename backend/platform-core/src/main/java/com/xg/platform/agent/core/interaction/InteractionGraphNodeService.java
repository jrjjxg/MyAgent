package com.xg.platform.agent.core.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentGraphMessageType;
import com.xg.platform.agent.core.AgentGraphToolCall;
import com.xg.platform.agent.core.AgentModelStep;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentPromptRequest;
import com.xg.platform.agent.core.AgentPromptService;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.agent.core.ExecutionSource;
import com.xg.platform.agent.core.SkillRuntimeSupport;
import com.xg.platform.agent.core.ToolExecutionGuard;
import com.xg.platform.agent.core.ToolUseLimits;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.chat.ChatRouteDecision;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import com.xg.platform.agent.core.chat.ChatRouterService;
import com.xg.platform.agent.core.research.scoping.ResearchScopingFlowService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.contracts.message.ResearchDraftRecord;
import com.xg.platform.contracts.message.ResearchDraftStatus;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.message.RunEventType;
import com.xg.platform.contracts.message.ThreadFileReference;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.graph.InteractionGraphNodes;
import com.xg.platform.graph.InteractionState;
import com.xg.platform.graph.ResearchScopingState;
import com.xg.platform.graph.RunEventConsumerRegistry;
import com.xg.platform.memory.ContextAssembler;
import com.xg.platform.memory.DocumentChunk;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.memory.RetrievedChunk;
import com.xg.platform.runtime.LongTermMemoryRepository;
import com.xg.platform.runtime.MemoryEventPayload;
import com.xg.platform.runtime.MemoryEventPublisher;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.ResearchDraftRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.ThreadMemorySnapshotRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.tools.SkillRegistry;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolExecutionRequest;
import com.xg.platform.tools.ToolExecutionResult;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;
import org.bsc.langgraph4j.state.AppenderChannel;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InteractionGraphNodeService implements InteractionGraphNodes {

    private static final Logger logger = Logger.getLogger(InteractionGraphNodeService.class.getName());
    private static final String MODEL_CONTEXT_PROPERTY = "modelContext";
    private static final String DEGRADED_PROPERTY = "degraded";
    private static final String DEGRADATION_REASON_PROPERTY = "degradationReason";
    private static final String DEGRADATION_ERROR_PROPERTY = "degradationError";
    private static final int DOC_MODEL_DOCUMENT_LIMIT = 6;
    private static final int DOC_MODEL_SECTION_LIMIT = 8;
    private static final int DOC_MODEL_MATCH_LIMIT = 5;
    private static final int DOC_MODEL_EVIDENCE_LIMIT = 3;
    private static final int DOC_MODEL_SNIPPET_LIMIT = 220;
    private static final int DOC_MODEL_SUMMARY_LIMIT = 320;

    private final ConversationMemoryService conversationMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final MemoryContextFormatter memoryContextFormatter;
    private final ResearchDraftRepository researchDraftRepository;
    private final ThreadRuntimeService threadRuntimeService;
    private final ThreadMemorySnapshotRepository threadMemorySnapshotRepository;
    private final MessageRepository messageRepository;
    private final RunEventRepository runEventRepository;
    private final MemoryEventPublisher memoryEventPublisher;
    private final ArtifactService artifactService;
    private final WorkspaceManager workspaceManager;
    private final DocumentStore documentStore;
    private final ContextAssembler contextAssembler;
    private final DocumentIngestService documentIngestService;
    private final ChatRouterService chatRouterService;
    private final AgentPromptService agentPromptService;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final AgentToolService agentToolService;
    private final SkillRegistry skillRegistry;
    private final ResearchScopingFlowService researchScopingFlowService;
    private final RunEventConsumerRegistry runEventConsumerRegistry;
    private final ObjectMapper objectMapper;
    private final DocumentQueryPlanner documentQueryPlanner = new DocumentQueryPlanner();
    private final boolean logAgentFlow;
    private final int maxToolCalls;
    private final int maxSearchCalls;
    private final int maxFetchCalls;
    private final int minVerifiedSources;
    private final long timeoutMs;

    public InteractionGraphNodeService(ConversationMemoryService conversationMemoryService,
                                       LongTermMemoryRepository longTermMemoryRepository,
                                       MemoryContextFormatter memoryContextFormatter,
                                       ResearchDraftRepository researchDraftRepository,
                                       ThreadRuntimeService threadRuntimeService,
                                       ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                       MessageRepository messageRepository,
                                       RunEventRepository runEventRepository,
                                       MemoryEventPublisher memoryEventPublisher,
                                       ArtifactService artifactService,
                                       WorkspaceManager workspaceManager,
                                       DocumentStore documentStore,
                                       ContextAssembler contextAssembler,
                                       DocumentIngestService documentIngestService,
                                       ChatRouterService chatRouterService,
                                       AgentPromptService agentPromptService,
                                       AgentTurnExecutionSupport agentTurnExecutionSupport,
                                       AgentToolService agentToolService,
                                       SkillRegistry skillRegistry,
                                       ResearchScopingFlowService researchScopingFlowService,
                                       RunEventConsumerRegistry runEventConsumerRegistry,
                                       ObjectMapper objectMapper,
                                       boolean logAgentFlow,
                                       int maxToolCalls,
                                       int maxSearchCalls,
                                       int maxFetchCalls,
                                       int minVerifiedSources,
                                       long timeoutMs) {
        this.conversationMemoryService = conversationMemoryService;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.memoryContextFormatter = memoryContextFormatter;
        this.researchDraftRepository = researchDraftRepository;
        this.threadRuntimeService = threadRuntimeService;
        this.threadMemorySnapshotRepository = threadMemorySnapshotRepository;
        this.messageRepository = messageRepository;
        this.runEventRepository = runEventRepository;
        this.memoryEventPublisher = memoryEventPublisher;
        this.artifactService = artifactService;
        this.workspaceManager = workspaceManager;
        this.documentStore = documentStore;
        this.contextAssembler = contextAssembler;
        this.documentIngestService = documentIngestService;
        this.chatRouterService = chatRouterService;
        this.agentPromptService = agentPromptService;
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.agentToolService = agentToolService;
        this.skillRegistry = skillRegistry;
        this.researchScopingFlowService = researchScopingFlowService;
        this.runEventConsumerRegistry = runEventConsumerRegistry;
        this.objectMapper = objectMapper;
        this.logAgentFlow = logAgentFlow;
        this.maxToolCalls = maxToolCalls;
        this.maxSearchCalls = maxSearchCalls;
        this.maxFetchCalls = maxFetchCalls;
        this.minVerifiedSources = minVerifiedSources;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Map<String, Object> loadShortTermMemory(InteractionState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ThreadMemoryView memoryView = conversationMemoryService.threadMemoryView(userId, threadId);
        return Map.of(
                InteractionState.MEMORY_VIEW, memoryView,
                InteractionState.SESSION_SUMMARY, memoryView.summary()
        );
    }

    @Override
    public Map<String, Object> loadLongTermMemory(InteractionState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        return Map.of(
                InteractionState.LONG_TERM_MEMORY,
                memoryContextFormatter.formatLongTermMemory(longTermMemoryRepository.listActive(userId), threadId)
        );
    }

    @Override
    public Map<String, Object> loadDraftContext(InteractionState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ResearchDraftRecord draft = researchDraftRepository.findActiveDraft(userId, threadId).orElse(null);
        if (draft == null || draft.status() == ResearchDraftStatus.STARTED) {
            return Map.of();
        }
        return Map.of(InteractionState.CURRENT_DRAFT, draft);
    }

    @Override
    public Map<String, Object> routeInteraction(InteractionState state) {
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Interaction request is missing"));
        InteractionMode interactionMode = request.interactionMode() == null
                ? InteractionMode.CHAT
                : request.interactionMode();
        if (interactionMode == InteractionMode.DEEP_RESEARCH) {
            return Map.of(
                    InteractionState.ROUTE_KIND, ChatRouteKind.RESEARCH_DRAFT,
                    InteractionState.WORKFLOW, "research-draft",
                    InteractionState.TOOLS_ENABLED, false
            );
        }
        ChatRouteDecision routeDecision = chatRouterService.route(buildRoutingRequest(state), documentStore.listDocuments(
                state.userId().orElseThrow(),
                state.threadId().orElseThrow()
        ));
        return Map.of(
                InteractionState.ROUTE_KIND, routeDecision.routeKind(),
                InteractionState.WORKFLOW, routeDecision.workflow(),
                InteractionState.TOOLS_ENABLED, routeDecision.toolsEnabled()
        );
    }

    @Override
    public Map<String, Object> runScopingFrame(InteractionState state) {
        return runScopingFrame(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> runScopingFrame(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Research scoping request is missing"));
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView()
                .orElseGet(() -> conversationMemoryService.threadMemoryView(
                        state.userId().orElseThrow(),
                        state.threadId().orElseThrow()
                ));
        return researchScopingFlowService.runScopingFrame(
                state.userId().orElseThrow(),
                state.threadId().orElseThrow(),
                request,
                memoryView,
                state.longTermMemory().orElse(""),
                state.<ResearchDraftRecord>currentDraft().orElse(null),
                runEventConsumer
        );
    }

    @Override
    public Map<String, Object> persistDraft(InteractionState state) {
        return researchScopingFlowService.persistDraft(new ResearchScopingState(state.data()));
    }

    @Override
    public Map<String, Object> persistAssistantMessage(InteractionState state) {
        return persistAssistantMessage(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> persistAssistantMessage(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return researchScopingFlowService.persistAssistantMessage(new ResearchScopingState(state.data()), runEventConsumer);
    }

    @Override
    public Map<String, Object> prepareAgentStep(InteractionState state) {
        return prepareAgentStep(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> prepareAgentStep(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Interaction request is missing"));
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView().orElseThrow();
        ChatRouteKind routeKind = state.routeKind().map(ChatRouteKind.class::cast).orElseThrow();
        String runId = state.runId().orElseThrow();
        List<String> selectedDocumentIds = selectedDocumentIds(request);
        List<ArtifactRecord> artifacts = artifactService.listArtifacts(userId, threadId);
        List<ThreadFileReference> uploadedFiles = uploadedFiles(userId, artifacts);
        List<ThreadFileReference> inputImages = resolveInputImages(userId, artifacts, request);
        List<DocumentRecord> documents = maybeLoadDocuments(userId, threadId, routeKind, runId, selectedDocumentIds);
        List<RetrievedChunk> retrievedChunks = routeKind == ChatRouteKind.DOCUMENT_QA
                ? List.of()
                : retrieveChunks(userId, request.content(), documents);
        MessageRecord userMessage = persistMessage(
                userId,
                threadId,
                MessageRole.USER,
                renderPersistedUserContent(request.content(), inputImages),
                InteractionMode.CHAT,
                runId,
                null
        );
        String providerId = agentTurnExecutionSupport.resolveProviderId(userId, request.providerId());
        List<ToolDescriptor> availableTools = availableTools(userId);
        List<SkillDescriptor> availableSkills = skillRegistry.listDescriptors(userId);
        List<String> activeSkillIds = loadPersistedActiveSkillIds(userId, threadId);
        ToolUseLimits toolUseLimits = routeKind == ChatRouteKind.RESEARCH_DRAFT
                ? null
                : new ToolUseLimits(maxToolCalls, maxSearchCalls, maxFetchCalls, 0, minVerifiedSources, timeoutMs);
        publishEvent(userId, threadId, runId, RunEventType.MESSAGE_ACCEPTED, Map.of(
                "messageId", userMessage.messageId(),
                "interactionMode", InteractionMode.CHAT.name()
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.RUN_STARTED, Map.of(
                "providerId", providerId,
                "interactionMode", InteractionMode.CHAT.name(),
                "routeKind", routeKind.name(),
                "workflow", workflowFor(routeKind),
                "toolsEnabled", !availableTools.isEmpty()
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.AGENT_SELECTED, Map.of(
                "agentId", routeKind == ChatRouteKind.DOCUMENT_QA ? "docs-agent" : "general-agent",
                "capability", routeKind == ChatRouteKind.DOCUMENT_QA ? "DOCS" : "GENERAL"
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.ROUTE_SELECTED, Map.of(
                "interactionMode", InteractionMode.CHAT.name(),
                "routeKind", routeKind.name(),
                "workflow", workflowFor(routeKind),
                "toolsEnabled", !availableTools.isEmpty()
        ), runEventConsumer);

        List<AgentGraphMessage> graphMessages = new ArrayList<>(memoryView.recentMessages().stream()
                .map(AgentGraphMessage::fromMessageRecord)
                .toList());
        graphMessages.add(AgentGraphMessage.user(userMessage));
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(InteractionState.RUN_ID, runId);
        updates.put(InteractionState.USER_MESSAGE, userMessage);
        updates.put(InteractionState.PROVIDER_ID, providerId);
        updates.put(InteractionState.ARTIFACTS, List.copyOf(artifacts));
        updates.put(InteractionState.UPLOADED_FILES, List.copyOf(uploadedFiles));
        updates.put(InteractionState.INPUT_IMAGES, List.copyOf(inputImages));
        updates.put(InteractionState.AVAILABLE_DOCUMENTS, List.copyOf(documents));
        updates.put(InteractionState.RETRIEVED_CHUNKS, List.copyOf(retrievedChunks));
        updates.put(InteractionState.AVAILABLE_TOOLS, List.copyOf(availableTools));
        updates.put(InteractionState.AVAILABLE_SKILLS, List.copyOf(availableSkills));
        updates.put(InteractionState.ACTIVE_SKILL_IDS, List.copyOf(activeSkillIds));
        if (toolUseLimits != null) {
            updates.put(InteractionState.TOOL_USE_LIMITS, toolUseLimits);
        }
        updates.put(InteractionState.CURRENT_USER_GRAPH_MESSAGE_ID, userMessage.messageId());
        updates.put(InteractionState.MESSAGES, AppenderChannel.ReplaceAllWith.of(graphMessages));
        updates.putAll(initializeDocumentQaState(userId, request.content(), routeKind, documents));
        return Map.copyOf(updates);
    }

    @Override
    public Map<String, Object> agent(InteractionState state) {
        AgentStepContext stepContext = buildAgentStepContext(state);
        AgentGraphMessage assistantMessage;
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String runId = state.runId().orElseThrow();
        Consumer<RunEvent> runEventConsumer = runEventConsumerRegistry.resolve(state.runContextKey().orElse(null));
        StepEventEmitter stepEventEmitter = new StepEventEmitter(userId, threadId, runId, runEventConsumer);
        try {
            AgentModelStep step = agentTurnExecutionSupport.runSingleStep(
                    stepContext.providerId(),
                    stepContext.request(),
                    state.messages(),
                    state.currentUserGraphMessageId().orElse(null),
                    stepContext.prompt(),
                    stepContext.availableTools(),
                    stepEventEmitter
            );
            assistantMessage = AgentGraphMessage.assistant(
                    UUID.randomUUID().toString(),
                    step.content(),
                    step.toolCalls(),
                    step.assistantProperties()
            );
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING,
                    "agent step failed thread=" + state.threadId().orElse("")
                            + " run=" + state.runId().orElse("")
                            + " provider=" + stepContext.providerId(),
                    exception);
            assistantMessage = AgentGraphMessage.assistant(
                    UUID.randomUUID().toString(),
                    buildFallbackAssistantContent(state, exception),
                    List.of(),
                    degradedProperties("model-step-fallback", safeErrorMessage(exception))
            );
        }
        return Map.of(
                InteractionState.PROMPT, stepContext.prompt(),
                InteractionState.AVAILABLE_TOOLS, List.copyOf(stepContext.availableTools()),
                InteractionState.MESSAGES, List.of(assistantMessage)
        );
    }

    @Override
    public Map<String, Object> executeTools(InteractionState state) {
        return executeTools(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> executeTools(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        if (state.messages().isEmpty()) {
            return Map.of();
        }
        AgentGraphMessage assistantMessage = state.messages().get(state.messages().size() - 1);
        if (assistantMessage.type() != AgentGraphMessageType.ASSISTANT || !assistantMessage.hasToolCalls()) {
            return Map.of();
        }
        List<AgentGraphMessage> toolMessages = new ArrayList<>();
        List<ExecutionSource> sources = new ArrayList<>(state.<ExecutionSource>sources());
        List<String> activeSkillIds = new ArrayList<>(state.activeSkillIds());
        List<String> actions = new ArrayList<>(state.actions());
        ToolUseLimits toolUseLimits = state.<ToolUseLimits>toolUseLimits().orElse(null);
        boolean documentQaRoute = state.routeKind().map(ChatRouteKind.class::cast).orElse(ChatRouteKind.CHAT) == ChatRouteKind.DOCUMENT_QA;
        String question = state.<PostMessageRequest>request()
                .map(PostMessageRequest::content)
                .orElse("");
        List<String> knownSectionTitles = documentQaRoute
                ? knownSectionTitles(state.userId().orElseThrow(), state.<DocumentRecord>availableDocuments())
                : List.of();
        DocumentQaScratchpad scratchpad = documentQaRoute
                ? state.<DocumentQaScratchpad>documentScratchpad().orElseGet(() -> defaultDocumentScratchpad(question, state.<DocumentRecord>availableDocuments()))
                : null;
        String documentPhase = documentQaRoute ? state.documentPhase().orElse("PLAN") : "";
        List<String> documentSearchHints = documentQaRoute
                ? new ArrayList<>(state.documentSearchHints())
                : List.of();
        for (AgentGraphToolCall toolCall : assistantMessage.toolCalls()) {
            JsonNode toolOutput;
            try {
                ToolDescriptor tool = agentToolService.requireTool(state.userId().orElseThrow(), toolCall.name());
                toolOutput = executeToolCall(state, runEventConsumer, toolUseLimits, tool, toolCall, sources, activeSkillIds);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING,
                        "tool resolution or execution failed tool=" + toolCall.name()
                                + " thread=" + state.threadId().orElse("")
                                + " run=" + state.runId().orElse(""),
                        exception);
                publishEvent(
                        state.userId().orElseThrow(),
                        state.threadId().orElseThrow(),
                        state.runId().orElseThrow(),
                        RunEventType.TOOL_FAILED,
                        Map.of(
                                "providerId", state.providerId().orElse(""),
                                "toolName", toolCall.name(),
                                "error", safeErrorMessage(exception)
                        ),
                        runEventConsumer
                );
                toolOutput = toolFailurePayload(toolCall.name(), safeErrorMessage(exception));
            }
            Map<String, Object> messageProperties = degradedToolMessageProperties(toolCall.name(), toolOutput);
            if (documentQaRoute) {
                scratchpad = updateDocumentScratchpad(question, scratchpad, toolCall.name(), toolOutput, knownSectionTitles);
                documentPhase = documentQueryPlanner.determinePhase(documentPhase, toolCall.name(), scratchpad);
                documentSearchHints = new ArrayList<>(documentQueryPlanner.buildSearchHints(question, scratchpad, knownSectionTitles));
                String modelContext = compressDocumentToolOutput(toolCall.name(), toolOutput);
                if (!modelContext.isBlank()) {
                    messageProperties = mergeMessageProperties(messageProperties, Map.of(MODEL_CONTEXT_PROPERTY, modelContext));
                }
            }
            toolMessages.add(AgentGraphMessage.tool(
                    UUID.randomUUID().toString(),
                    toolCall.name(),
                    toolCall.id(),
                    toolOutput == null ? "{}" : toolOutput.toString(),
                    messageProperties
            ));
            actions.add("tool:" + toolCall.name());
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(InteractionState.MESSAGES, toolMessages);
        updates.put(InteractionState.SOURCES, List.copyOf(deduplicateSources(sources)));
        updates.put(InteractionState.ACTIVE_SKILL_IDS, List.copyOf(activeSkillIds));
        updates.put(InteractionState.ACTIONS, List.copyOf(actions));
        if (documentQaRoute && scratchpad != null) {
            updates.put(InteractionState.DOCUMENT_SCRATCHPAD, scratchpad);
            updates.put(InteractionState.DOCUMENT_WORKING_MEMORY, scratchpad.render());
            updates.put(InteractionState.DOCUMENT_PHASE, documentPhase);
            updates.put(InteractionState.DOCUMENT_SEARCH_HINTS, List.copyOf(documentSearchHints));
        }
        return Map.copyOf(updates);
    }

    @Override
    public Map<String, Object> persistTurnArtifacts(InteractionState state) {
        return persistTurnArtifacts(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> persistTurnArtifacts(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        if (state.routeKind().map(ChatRouteKind.class::cast).orElse(ChatRouteKind.CHAT) == ChatRouteKind.RESEARCH_DRAFT) {
            return Map.of();
        }
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String runId = state.runId().orElseThrow();
        AgentGraphMessage finalAssistant = findLastFinalAssistantMessage(state.messages());
        if (finalAssistant == null) {
            throw new IllegalStateException("Final assistant message is missing");
        }
        String finalContent = appendSourceAppendix(finalAssistant.content(), state.<ExecutionSource>sources());
        for (String segment : split(finalContent, 180)) {
            publishEvent(userId, threadId, runId, RunEventType.MESSAGE_DELTA, Map.of("delta", segment), runEventConsumer);
        }
        MessageRecord assistantMessage = persistMessage(
                userId,
                threadId,
                MessageRole.ASSISTANT,
                finalContent,
                InteractionMode.CHAT,
                runId,
                null
        );
        return Map.of(
                InteractionState.ASSISTANT_CONTENT, finalContent,
                InteractionState.ASSISTANT_MESSAGE, assistantMessage
        );
    }

    @Override
    public Map<String, Object> publishTurnEvents(InteractionState state) {
        return publishTurnEvents(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> publishTurnEvents(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        if (state.routeKind().map(ChatRouteKind.class::cast).orElse(ChatRouteKind.CHAT) == ChatRouteKind.RESEARCH_DRAFT) {
            return researchScopingFlowService.publishScopingEvents(new ResearchScopingState(state.data()), runEventConsumer);
        }
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String runId = state.runId().orElseThrow();
        MessageRecord assistantMessage = state.<MessageRecord>assistantMessage()
                .orElseThrow(() -> new IllegalStateException("Assistant message is missing"));
        persistActiveSkillIds(userId, threadId, state.activeSkillIds());
        publishEvent(userId, threadId, runId, RunEventType.MESSAGE_COMPLETED, Map.of(
                "messageId", assistantMessage.messageId()
        ), runEventConsumer);
        publishMemoryEvent(RunEventType.MESSAGE_COMPLETED, userId, threadId, null, assistantMessage.messageId());
        List<ExecutionSource> sources = state.<ExecutionSource>sources();
        ChatRouteKind routeKind = state.routeKind().map(ChatRouteKind.class::cast).orElse(ChatRouteKind.CHAT);
        DegradationSummary degradation = collectDegradationSummary(state.messages());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summarize(state.assistantContent().orElse("")));
        payload.put("providerId", state.providerId().orElse(""));
        payload.put("routeKind", routeKind.name());
        payload.put("workflow", workflowFor(routeKind));
        payload.put("toolsEnabled", state.toolsEnabled().orElse(false));
        payload.put("finalMessageId", assistantMessage.messageId());
        payload.put("sources", List.copyOf(sources));
        payload.put("sourceCount", sources.size());
        payload.put("usedVerifiedSources", sources.stream().filter(ExecutionSource::verified).count());
        payload.put("degraded", degradation.degraded());
        payload.put("degradationReasons", degradation.reasons());
        publishEvent(userId, threadId, runId, RunEventType.RUN_COMPLETED, payload, runEventConsumer);
        threadRuntimeService.touchThread(userId, threadId);
        return Map.of(
                InteractionState.EVENTS_PUBLISHED, true,
                InteractionState.RESULT, "completed"
        );
    }

    private AgentExecutionRequest buildRoutingRequest(InteractionState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Interaction request is missing"));
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView().orElseThrow();
        List<ArtifactRecord> artifacts = artifactService.listArtifacts(userId, threadId);
        return new AgentExecutionRequest(
                userId,
                threadId,
                UUID.randomUUID().toString(),
                request.content(),
                null,
                request.providerId(),
                List.of(),
                List.of(),
                "auto",
                artifacts,
                uploadedFiles(userId, artifacts),
                resolveInputImages(userId, artifacts, request),
                memoryView.recentMessages(),
                state.sessionSummary().orElse(""),
                state.longTermMemory().orElse(""),
                null,
                null,
                null,
                List.of(),
                selectedDocumentIds(request)
        );
    }

    private AgentStepContext buildAgentStepContext(InteractionState state) {
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Interaction request is missing"));
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ChatRouteKind routeKind = state.routeKind().map(ChatRouteKind.class::cast).orElseThrow();
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView().orElseThrow();
        List<ArtifactRecord> artifacts = state.<ArtifactRecord>artifacts();
        List<ThreadFileReference> uploadedFiles = state.<ThreadFileReference>uploadedFiles();
        List<ThreadFileReference> inputImages = state.<ThreadFileReference>inputImages();
        ToolUseLimits toolUseLimits = state.<ToolUseLimits>toolUseLimits().orElse(null);
        List<String> selectedDocumentIds = selectedDocumentIds(request);
        AgentExecutionRequest executionRequest = new AgentExecutionRequest(
                userId,
                threadId,
                state.runId().orElseThrow(),
                request.content(),
                routeKind == ChatRouteKind.DOCUMENT_QA ? "docs-agent" : "general-agent",
                request.providerId(),
                List.of(),
                List.of(),
                "auto",
                artifacts,
                uploadedFiles,
                inputImages,
                memoryView.recentMessages(),
                state.sessionSummary().orElse(""),
                state.longTermMemory().orElse(""),
                routeKind,
                null,
                toolUseLimits,
                state.activeSkillIds(),
                selectedDocumentIds
        );
        List<ToolDescriptor> availableTools = state.<ToolDescriptor>availableTools();
        List<SkillDescriptor> availableSkills = state.<SkillDescriptor>availableSkills();
        List<com.xg.platform.tools.SkillDefinition> loadedSkills = resolveLoadedSkills(userId, state.activeSkillIds());
        List<ToolDescriptor> prioritizedTools = SkillRuntimeSupport.prioritizeToolsForSkills(availableTools, loadedSkills);
        String currentPhase = routeKind == ChatRouteKind.DOCUMENT_QA
                ? state.documentPhase().orElse("PLAN")
                : "SYNTHESIZE";
        String workingMemory = routeKind == ChatRouteKind.DOCUMENT_QA
                ? state.documentWorkingMemory().orElse("")
                : "";
        String readingPlan = routeKind == ChatRouteKind.DOCUMENT_QA
                ? state.documentReadingPlan().orElse("")
                : "";
        List<String> searchHints = routeKind == ChatRouteKind.DOCUMENT_QA
                ? state.documentSearchHints()
                : List.of();
        String prompt = agentPromptService.renderPrompt(new AgentPromptRequest(
                routeKind == ChatRouteKind.DOCUMENT_QA ? "docs-agent" : "general-agent",
                request.content(),
                routeKind,
                List.of(),
                prioritizedTools,
                memoryView.recentMessages(),
                memoryView.pendingHistoricalMessages(),
                uploadedFiles,
                artifacts,
                state.<DocumentRecord>availableDocuments(),
                state.<RetrievedChunk>retrievedChunks(),
                state.sessionSummary().orElse(""),
                state.longTermMemory().orElse(""),
                currentPhase,
                state.actions(),
                workingMemory,
                selectedDocumentIds,
                readingPlan,
                searchHints,
                availableSkills,
                List.of(),
                loadedSkills
        ));
        return new AgentStepContext(
                state.providerId().orElseThrow(),
                executionRequest,
                prompt,
                prioritizedTools
        );
    }

    private List<DocumentRecord> maybeLoadDocuments(String userId,
                                                    String threadId,
                                                    ChatRouteKind routeKind,
                                                    String runId,
                                                    List<String> selectedDocumentIds) {
        if (routeKind != ChatRouteKind.DOCUMENT_QA) {
            return List.of();
        }
        List<DocumentRecord> documents = documentStore.listDocuments(userId, threadId);
        if (selectedDocumentIds != null && !selectedDocumentIds.isEmpty()) {
            Set<String> selected = new LinkedHashSet<>(selectedDocumentIds);
            documents = documents.stream()
                    .filter(document -> selected.contains(document.documentId()))
                    .toList();
        }
        return documentIngestService.ensureReadyDocuments(userId, threadId, documents, runId, delta -> {
        });
    }

    private Map<String, Object> initializeDocumentQaState(String userId,
                                                          String question,
                                                          ChatRouteKind routeKind,
                                                          List<DocumentRecord> documents) {
        if (routeKind != ChatRouteKind.DOCUMENT_QA) {
            return Map.of();
        }
        List<String> sectionTitles = knownSectionTitles(userId, documents);
        DocumentQuestionType questionType = documentQueryPlanner.classify(question);
        String leadDocumentName = documents.isEmpty() ? "current document scope" : documents.get(0).name();
        DocumentQaScratchpad scratchpad = documentQueryPlanner.initializeScratchpad(question, questionType, leadDocumentName);
        String readingPlan = documentQueryPlanner.buildReadingPlan(questionType, leadDocumentName, sectionTitles);
        List<String> searchHints = documentQueryPlanner.buildSearchHints(question, scratchpad, sectionTitles);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(InteractionState.DOCUMENT_READING_PLAN, readingPlan);
        updates.put(InteractionState.DOCUMENT_WORKING_MEMORY, scratchpad.render());
        updates.put(InteractionState.DOCUMENT_PHASE, "PLAN");
        updates.put(InteractionState.DOCUMENT_SEARCH_HINTS, List.copyOf(searchHints));
        updates.put(InteractionState.DOCUMENT_SCRATCHPAD, scratchpad);
        return Map.copyOf(updates);
    }

    private List<RetrievedChunk> retrieveChunks(String userId, String query, List<DocumentRecord> documents) {
        if (documents == null || documents.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        String normalizedQuery = query.trim();
        return contextAssembler.retrieve(normalizedQuery, documents, document -> loadChunks(userId, document), 8);
    }

    private List<DocumentChunk> loadChunks(String userId, DocumentRecord document) {
        if (document.chunkIndexArtifactId() == null || document.chunkIndexArtifactId().isBlank()) {
            return List.of();
        }
        try {
            ArtifactRecord artifact = artifactService.findArtifactByWorkspace(userId, document.workspaceId(), document.chunkIndexArtifactId());
            Path artifactPath = artifactService.resolveArtifactPath(userId, artifact);
            return objectMapper.readValue(
                    artifactPath.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, com.xg.platform.memory.DocumentChunk.class)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load chunk index for document " + document.documentId(), exception);
        }
    }

    private List<String> knownSectionTitles(String userId, List<DocumentRecord> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sectionTitles = new LinkedHashSet<>();
        for (DocumentRecord document : documents) {
            for (DocumentChunk chunk : loadChunks(userId, document)) {
                if (chunk.sectionTitle() == null || chunk.sectionTitle().isBlank()) {
                    continue;
                }
                sectionTitles.add(chunk.sectionTitle().trim());
                if (sectionTitles.size() >= 16) {
                    return documentQueryPlanner.knownSectionTitles(new ArrayList<>(sectionTitles));
                }
            }
        }
        return documentQueryPlanner.knownSectionTitles(new ArrayList<>(sectionTitles));
    }

    private DocumentQaScratchpad defaultDocumentScratchpad(String question, List<DocumentRecord> documents) {
        DocumentQuestionType questionType = documentQueryPlanner.classify(question);
        String leadDocumentName = documents == null || documents.isEmpty()
                ? "current document scope"
                : documents.get(0).name();
        return documentQueryPlanner.initializeScratchpad(question, questionType, leadDocumentName);
    }

    private DocumentQaScratchpad updateDocumentScratchpad(String question,
                                                          DocumentQaScratchpad scratchpad,
                                                          String toolName,
                                                          JsonNode toolOutput,
                                                          List<String> knownSectionTitles) {
        if (scratchpad == null) {
            return null;
        }
        if ("search_document".equals(toolName)) {
            return documentQueryPlanner.updateAfterSearch(question, scratchpad, toolOutput, knownSectionTitles);
        }
        if ("read_document".equals(toolName)) {
            return documentQueryPlanner.updateAfterRead(question, scratchpad, toolOutput);
        }
        return scratchpad;
    }

    private String compressDocumentToolOutput(String toolName, JsonNode output) {
        if (output == null || output.isNull() || output.isMissingNode()) {
            return "";
        }
        ObjectNode compact = objectMapper.createObjectNode();
        compact.put("toolName", toolName == null ? "" : toolName);
        switch (toolName) {
            case "list_workspace_documents" -> compressListWorkspaceDocuments(output, compact);
            case "inspect_document" -> compressInspectDocument(output, compact);
            case "list_document_sections" -> compressListDocumentSections(output, compact);
            case "search_document" -> compressSearchDocument(output, compact);
            case "read_document" -> compressReadDocument(output, compact);
            default -> compact.put("summary", truncateForModel(output.toString(), DOC_MODEL_SUMMARY_LIMIT));
        }
        return compact.toString();
    }

    private void compressListWorkspaceDocuments(JsonNode output, ObjectNode compact) {
        compact.put("scope", output.path("scope").asText(""));
        compact.put("documentCount", output.path("documentCount").asInt(0));
        ArrayNode documents = compact.putArray("documents");
        int index = 0;
        for (JsonNode document : output.path("documents")) {
            if (index++ >= DOC_MODEL_DOCUMENT_LIMIT) {
                break;
            }
            documents.add(objectMapper.createObjectNode()
                    .put("documentId", document.path("documentId").asText(""))
                    .put("name", document.path("name").asText(""))
                    .put("status", document.path("status").asText(""))
                    .put("kind", document.path("kind").asText(""))
                    .put("pageCount", document.path("pageCount").asInt(0))
                    .put("chunkCount", document.path("chunkCount").asInt(0)));
        }
    }

    private void compressInspectDocument(JsonNode output, ObjectNode compact) {
        copyDocumentBasics(output, compact);
        compact.put("readable", output.path("readable").asBoolean(false));
        compact.put("sectionCount", output.path("sectionCount").asInt(0));
        if (output.hasNonNull("reason")) {
            compact.put("reason", output.path("reason").asText(""));
        }
        ArrayNode sections = compact.putArray("sectionPreview");
        int index = 0;
        for (JsonNode section : output.path("sectionPreview")) {
            if (index++ >= DOC_MODEL_SECTION_LIMIT) {
                break;
            }
            sections.add(compactSection(section));
        }
    }

    private void compressListDocumentSections(JsonNode output, ObjectNode compact) {
        compact.put("documentId", output.path("documentId").asText(""));
        compact.put("documentName", output.path("documentName").asText(""));
        compact.put("sectionCount", output.path("sectionCount").asInt(0));
        ArrayNode sections = compact.putArray("sections");
        int index = 0;
        for (JsonNode section : output.path("sections")) {
            if (index++ >= DOC_MODEL_SECTION_LIMIT) {
                break;
            }
            sections.add(compactSection(section));
        }
    }

    private void compressSearchDocument(JsonNode output, ObjectNode compact) {
        compact.put("query", output.path("query").asText(""));
        compact.put("matchCount", output.path("matchCount").asInt(0));
        if (output.hasNonNull("note")) {
            compact.put("note", output.path("note").asText(""));
        }
        ArrayNode matches = compact.putArray("matches");
        int index = 0;
        for (JsonNode match : output.path("matches")) {
            if (index++ >= DOC_MODEL_MATCH_LIMIT) {
                break;
            }
            matches.add(objectMapper.createObjectNode()
                    .put("documentName", match.path("documentName").asText(""))
                    .put("sectionTitle", match.path("sectionTitle").asText(""))
                    .put("pageStart", match.path("pageStart").asInt(0))
                    .put("pageEnd", match.path("pageEnd").asInt(0))
                    .put("score", match.path("score").asInt(0))
                    .put("snippet", truncateForModel(match.path("snippet").asText(""), DOC_MODEL_SNIPPET_LIMIT)));
        }
    }

    private void compressReadDocument(JsonNode output, ObjectNode compact) {
        compact.put("documentId", output.path("documentId").asText(""));
        compact.put("documentName", output.path("documentName").asText(""));
        compact.put("cursor", output.path("cursor").asText(""));
        compact.put("nextCursor", output.path("nextCursor").asText(""));
        compact.put("hasMore", output.path("hasMore").asBoolean(false));
        compact.put("chunkStart", output.path("chunkStart").asInt(0));
        compact.put("chunkEnd", output.path("chunkEnd").asInt(0));
        compact.put("pageStart", output.path("pageStart").asInt(0));
        compact.put("pageEnd", output.path("pageEnd").asInt(0));
        String content = output.path("content").asText("");
        compact.put("summary", truncateForModel(content, DOC_MODEL_SUMMARY_LIMIT));
        ArrayNode evidence = compact.putArray("evidence");
        for (String bullet : extractEvidenceBullets(content)) {
            evidence.add(bullet);
        }
    }

    private void copyDocumentBasics(JsonNode source, ObjectNode target) {
        target.put("documentId", source.path("documentId").asText(""));
        target.put("name", source.path("name").asText(""));
        target.put("status", source.path("status").asText(""));
        target.put("kind", source.path("kind").asText(""));
        target.put("pageCount", source.path("pageCount").asInt(0));
        target.put("chunkCount", source.path("chunkCount").asInt(0));
    }

    private ObjectNode compactSection(JsonNode section) {
        return objectMapper.createObjectNode()
                .put("sectionTitle", section.path("sectionTitle").asText(""))
                .put("pageStart", section.path("pageStart").asInt(0))
                .put("pageEnd", section.path("pageEnd").asInt(0))
                .put("chunkStart", section.path("chunkStart").asInt(0))
                .put("chunkEnd", section.path("chunkEnd").asInt(0));
    }

    private List<String> extractEvidenceBullets(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalized = content.replace("\r", "");
        LinkedHashSet<String> bullets = new LinkedHashSet<>();
        for (String rawLine : normalized.split("\n")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("##")) {
                continue;
            }
            bullets.add(truncateForModel(line, 140));
            if (bullets.size() >= DOC_MODEL_EVIDENCE_LIMIT) {
                break;
            }
        }
        if (bullets.isEmpty()) {
            bullets.add(truncateForModel(normalized.replaceAll("\\s+", " "), 140));
        }
        return List.copyOf(bullets);
    }

    private String truncateForModel(String text, int limit) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }

    private List<ToolDescriptor> availableTools(String userId) {
        return List.copyOf(agentToolService.listAvailableTools(userId));
    }

    private List<ThreadFileReference> uploadedFiles(String userId, List<ArtifactRecord> artifacts) {
        return artifacts.stream()
                .filter(artifact -> artifact.type() == ArtifactType.UPLOAD)
                .map(artifact -> new ThreadFileReference(
                        artifact.name(),
                        artifact.relativePath(),
                        artifactService.resolveArtifactPath(userId, artifact).toString(),
                        artifact.contentType(),
                        artifact.sizeBytes()
                ))
                .toList();
    }

    private List<ThreadFileReference> resolveInputImages(String userId, List<ArtifactRecord> artifacts, PostMessageRequest request) {
        if (request == null || request.imageArtifactIds() == null || request.imageArtifactIds().isEmpty()) {
            return List.of();
        }
        Map<String, ArtifactRecord> artifactsById = new LinkedHashMap<>();
        for (ArtifactRecord artifact : artifacts) {
            artifactsById.put(artifact.artifactId(), artifact);
        }
        Set<String> uniqueIds = new LinkedHashSet<>(request.imageArtifactIds());
        List<ThreadFileReference> resolved = new ArrayList<>();
        for (String artifactId : uniqueIds) {
            ArtifactRecord artifact = artifactsById.get(artifactId);
            if (artifact == null || artifact.contentType() == null || !artifact.contentType().toLowerCase().startsWith("image/")) {
                continue;
            }
            resolved.add(new ThreadFileReference(
                    artifact.name(),
                    artifact.relativePath(),
                    artifactService.resolveArtifactPath(userId, artifact).toString(),
                    artifact.contentType(),
                    artifact.sizeBytes()
            ));
        }
        return List.copyOf(resolved);
    }

    private final class StepEventEmitter implements AgentOutputEmitter {

        private final String userId;
        private final String threadId;
        private final String runId;
        private final Consumer<RunEvent> runEventConsumer;

        private StepEventEmitter(String userId,
                                 String threadId,
                                 String runId,
                                 Consumer<RunEvent> runEventConsumer) {
            this.userId = userId;
            this.threadId = threadId;
            this.runId = runId;
            this.runEventConsumer = runEventConsumer;
        }

        @Override
        public void emitText(String delta) {
            // Single-step text is surfaced through explicit process events, not the final assistant channel.
        }

        @Override
        public void emitEvent(RunEventType eventType, Object payload) {
            publishEvent(userId, threadId, runId, eventType, payload, runEventConsumer);
        }
    }

    private JsonNode executeToolCall(InteractionState state,
                                     Consumer<RunEvent> runEventConsumer,
                                     ToolUseLimits toolUseLimits,
                                     ToolDescriptor tool,
                                     AgentGraphToolCall toolCall,
                                     List<ExecutionSource> sources,
                                     List<String> activeSkillIds) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String runId = state.runId().orElseThrow();
        if (toolUseLimits != null && !toolUseLimits.tryAcquire(tool.name())) {
            publishEvent(userId, threadId, runId, RunEventType.RESEARCH_AGENT_BUDGET_EXHAUSTED, Map.of(
                    "providerId", state.providerId().orElse(""),
                    "toolName", tool.name(),
                    "maxToolCalls", toolUseLimits.maxToolCalls(),
                    "maxSearchCalls", toolUseLimits.maxSearchCalls(),
                    "maxFetchCalls", toolUseLimits.maxFetchCalls(),
                    "totalCalls", toolUseLimits.totalCalls(),
                    "searchCalls", toolUseLimits.searchCalls(),
                    "fetchCalls", toolUseLimits.fetchCalls(),
                    "reflectCalls", toolUseLimits.reflectCalls()
            ), runEventConsumer);
            return JsonNodeFactory.instance.objectNode()
                    .put("status", "error")
                    .put("toolName", tool.name())
                    .put("error", "Tool budget exhausted");
        }
        publishEvent(userId, threadId, runId, RunEventType.TOOL_STARTED, Map.of(
                "providerId", state.providerId().orElse(""),
                "toolName", tool.name()
        ), runEventConsumer);
        logFlow(() -> "tool execution started"
                + " provider=" + state.providerId().orElse("")
                + " tool=" + tool.name()
                + " thread=" + threadId
                + " run=" + runId
                + System.lineSeparator()
                + (toolCall.arguments() == null ? "{}" : toolCall.arguments().toString()));
        try {
            ToolExecutionResult result = ToolExecutionGuard.execute(
                    tool.name(),
                    resolveToolTimeoutMs(toolUseLimits),
                    () -> agentToolService.execute(new ToolExecutionRequest(
                            userId,
                            threadId,
                            runId,
                            tool,
                            toolCall.arguments(),
                            skillRegistry.snapshotForUser(userId),
                            activeSkillIds,
                            selectedDocumentIds(state.<PostMessageRequest>request().orElse(null))
                    ))
            );
            JsonNode output = result.output() == null ? JsonNodeFactory.instance.objectNode() : result.output();
            captureSources(tool.name(), output, sources, runEventConsumer, userId, threadId, runId);
            maybeActivateSkill(tool.name(), output, activeSkillIds);
            publishEvent(userId, threadId, runId, RunEventType.TOOL_COMPLETED, Map.of(
                    "providerId", state.providerId().orElse(""),
                    "toolName", tool.name()
            ), runEventConsumer);
            logFlow(() -> "tool execution completed"
                    + " provider=" + state.providerId().orElse("")
                    + " tool=" + tool.name()
                    + " thread=" + threadId
                    + " run=" + runId
                    + System.lineSeparator()
                    + output.toString());
            return output;
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "tool execution failed tool=" + tool.name() + " thread=" + threadId + " run=" + runId, exception);
            publishEvent(userId, threadId, runId, RunEventType.TOOL_FAILED, Map.of(
                    "providerId", state.providerId().orElse(""),
                    "toolName", tool.name(),
                    "error", safeErrorMessage(exception)
            ), runEventConsumer);
            return toolFailurePayload(tool.name(), safeErrorMessage(exception));
        }
    }

    private void maybeActivateSkill(String toolName, JsonNode output, List<String> activeSkillIds) {
        if (!"load_skill".equals(toolName)) {
            return;
        }
        String skillId = output.path("skillId").asText("").trim();
        if (!skillId.isBlank() && !activeSkillIds.contains(skillId)) {
            activeSkillIds.add(skillId);
        }
    }

    private List<com.xg.platform.tools.SkillDefinition> resolveLoadedSkills(String userId,
                                                                            List<String> activeSkillIds) {
        if (activeSkillIds == null || activeSkillIds.isEmpty()) {
            return List.of();
        }
        com.xg.platform.tools.SkillRuntimeSnapshot snapshot = skillRegistry.snapshotForUser(userId);
        List<com.xg.platform.tools.SkillDefinition> loadedSkills = new ArrayList<>();
        for (String activeSkillId : activeSkillIds) {
            if (activeSkillId == null || activeSkillId.isBlank()) {
                continue;
            }
            try {
                loadedSkills.add(skillRegistry.requireEnabledSkill(userId, activeSkillId, snapshot));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale skill ids so prompt generation remains resilient.
            }
        }
        return List.copyOf(loadedSkills);
    }

    private void captureSources(String toolName,
                                JsonNode output,
                                List<ExecutionSource> sources,
                                Consumer<RunEvent> runEventConsumer,
                                String userId,
                                String threadId,
                                String runId) {
        if ("web_search".equals(toolName)) {
            for (JsonNode resultNode : output.path("results")) {
                String url = resultNode.path("url").asText("").trim();
                String title = resultNode.path("title").asText(url).trim();
                if (url.isBlank() || title.isBlank()) {
                    continue;
                }
                ExecutionSource source = new ExecutionSource("WEB_RESULT", title, domainOf(url), url, false, false);
                sources.add(source);
                publishEvent(userId, threadId, runId, RunEventType.EVIDENCE_CANDIDATE_ADDED, Map.of(
                        "kind", source.kind(),
                        "title", source.title(),
                        "domain", source.domain(),
                        "url", source.url()
                ), runEventConsumer);
            }
            return;
        }
        if ("web_fetch".equals(toolName)) {
            String url = output.path("url").asText("").trim();
            String title = output.path("title").asText(url).trim();
            if (url.isBlank() || title.isBlank()) {
                return;
            }
            ExecutionSource source = new ExecutionSource("WEB_PAGE", title, domainOf(url), url, true, true);
            sources.add(source);
            publishEvent(userId, threadId, runId, RunEventType.EVIDENCE_VERIFIED_ADDED, Map.of(
                    "kind", source.kind(),
                    "title", source.title(),
                    "domain", source.domain(),
                    "url", source.url()
            ), runEventConsumer);
            return;
        }
        if ("weather".equals(toolName)) {
            JsonNode sourceNode = output.path("source");
            String url = sourceNode.path("url").asText("").trim();
            String title = sourceNode.path("title").asText("").trim();
            if (url.isBlank() || title.isBlank()) {
                return;
            }
            ExecutionSource source = new ExecutionSource(
                    "WEATHER_REPORT",
                    title,
                    sourceNode.path("domain").asText("wttr.in").trim(),
                    url,
                    true,
                    true
            );
            sources.add(source);
            publishEvent(userId, threadId, runId, RunEventType.EVIDENCE_VERIFIED_ADDED, Map.of(
                    "kind", source.kind(),
                    "title", source.title(),
                    "domain", source.domain(),
                    "url", source.url()
            ), runEventConsumer);
        }
    }

    private List<String> selectedDocumentIds(PostMessageRequest request) {
        if (request == null || request.documentIds() == null || request.documentIds().isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(request.documentIds()));
    }

    private List<String> loadPersistedActiveSkillIds(String userId, String threadId) {
        return threadMemorySnapshotRepository.findByThread(userId, threadId)
                .map(snapshot -> normalizeSkillIds(snapshot.activeSkillIds()))
                .orElse(List.of());
    }

    private void persistActiveSkillIds(String userId, String threadId, List<String> activeSkillIds) {
        List<String> normalizedSkillIds = normalizeSkillIds(activeSkillIds);
        var existing = threadMemorySnapshotRepository.findByThread(userId, threadId).orElse(null);
        threadMemorySnapshotRepository.save(userId, new com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord(
                threadId,
                userId,
                existing == null ? "" : existing.summary(),
                existing == null ? null : existing.lastCompactedMessageId(),
                existing == null ? List.of() : existing.pendingHistoricalMessages(),
                existing == null ? null : existing.recentEndMessageId(),
                existing == null ? 20 : existing.recentWindowSize(),
                existing == null ? null : existing.activeDraftId(),
                existing == null ? null : existing.activeTaskId(),
                existing == null ? null : existing.taskStage(),
                normalizedSkillIds,
                Instant.now()
        ));
    }

    private List<String> normalizeSkillIds(List<String> activeSkillIds) {
        if (activeSkillIds == null || activeSkillIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String activeSkillId : activeSkillIds) {
            if (activeSkillId == null || activeSkillId.isBlank()) {
                continue;
            }
            normalized.add(activeSkillId.trim());
        }
        return List.copyOf(normalized);
    }

    private List<ExecutionSource> deduplicateSources(List<ExecutionSource> sources) {
        Map<String, ExecutionSource> deduped = new LinkedHashMap<>();
        for (ExecutionSource source : sources) {
            ExecutionSource existing = deduped.get(source.url());
            if (existing == null || (!existing.verified() && source.verified())) {
                deduped.put(source.url(), source);
            }
        }
        return List.copyOf(deduped.values());
    }

    private AgentGraphMessage findLastFinalAssistantMessage(List<AgentGraphMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            AgentGraphMessage candidate = messages.get(index);
            if (candidate.type() == AgentGraphMessageType.ASSISTANT && !candidate.hasToolCalls()) {
                return candidate;
            }
        }
        return null;
    }

    private String appendSourceAppendix(String response, List<ExecutionSource> sources) {
        String normalized = response == null ? "" : response.trim();
        if (normalized.isBlank() || sources == null || sources.isEmpty() || hasSourceAppendix(normalized)) {
            return normalized;
        }
        StringBuilder builder = new StringBuilder(normalized)
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("## Sources")
                .append(System.lineSeparator());
        for (ExecutionSource source : deduplicateSources(sources)) {
            builder.append("- [")
                    .append(renderSourceLabel(source.kind()))
                    .append("] ")
                    .append(source.title());
            if (source.domain() != null && !source.domain().isBlank()) {
                builder.append(" | ").append(source.domain());
            }
            builder.append(" - ").append(source.url()).append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private String renderSourceLabel(String kind) {
        return switch (kind) {
            case "WEB_PAGE" -> "Web Page";
            case "WEATHER_REPORT" -> "Weather Data";
            default -> "Search Result";
        };
    }

    private boolean hasSourceAppendix(String response) {
        String normalized = response.toLowerCase();
        return normalized.contains("\n## sources") || normalized.contains("\n### sources");
    }

    private String domainOf(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private MessageRecord persistMessage(String userId,
                                         String threadId,
                                         MessageRole role,
                                         String content,
                                         InteractionMode interactionMode,
                                         String runId,
                                         String taskId) {
        return messageRepository.append(userId, new MessageRecord(
                UUID.randomUUID().toString(),
                threadId,
                role,
                content,
                interactionMode,
                runId,
                taskId,
                Instant.now()
        ));
    }

    private void publishEvent(String userId,
                              String threadId,
                              String runId,
                              RunEventType runEventType,
                              Object payload,
                              Consumer<RunEvent> runEventConsumer) {
        RunEvent event = new RunEvent(runId, threadId, runEventType.value(), Instant.now(), payload);
        runEventRepository.appendEvent(userId, threadId, event);
        runEventConsumer.accept(event);
    }

    private void publishMemoryEvent(RunEventType eventType,
                                    String userId,
                                    String threadId,
                                    String taskId,
                                    String messageId) {
        memoryEventPublisher.publish(new MemoryEventPayload(
                eventType.value(),
                userId,
                threadId,
                taskId,
                messageId,
                Instant.now()
        ));
    }

    private Iterable<String> split(String response, int chunkSize) {
        List<String> segments = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return segments;
        }
        for (int index = 0; index < response.length(); index += chunkSize) {
            int end = Math.min(response.length(), index + chunkSize);
            segments.add(response.substring(index, end));
        }
        return segments;
    }

    private String summarize(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private JsonNode toolFailurePayload(String toolName, String errorMessage) {
        return JsonNodeFactory.instance.objectNode()
                .put("status", "error")
                .put("toolName", toolName == null ? "" : toolName)
                .put("error", errorMessage == null || errorMessage.isBlank() ? "Tool execution failed" : errorMessage);
    }

    private long resolveToolTimeoutMs(ToolUseLimits toolUseLimits) {
        return toolUseLimits == null ? timeoutMs : toolUseLimits.timeoutMs();
    }

    private Map<String, Object> degradedToolMessageProperties(String toolName, JsonNode toolOutput) {
        if (!isToolFailurePayload(toolOutput)) {
            return Map.of();
        }
        return degradedProperties("tool-failed:" + (toolName == null ? "" : toolName), toolOutput.path("error").asText(""));
    }

    private boolean isToolFailurePayload(JsonNode toolOutput) {
        return toolOutput != null && "error".equalsIgnoreCase(toolOutput.path("status").asText(""));
    }

    private Map<String, Object> degradedProperties(String reason, String error) {
        return Map.of(
                DEGRADED_PROPERTY, true,
                DEGRADATION_REASON_PROPERTY, reason == null || reason.isBlank() ? "degraded" : reason,
                DEGRADATION_ERROR_PROPERTY, error == null ? "" : error
        );
    }

    private Map<String, Object> mergeMessageProperties(Map<String, Object> left, Map<String, Object> right) {
        if (left == null || left.isEmpty()) {
            return right == null ? Map.of() : Map.copyOf(right);
        }
        if (right == null || right.isEmpty()) {
            return Map.copyOf(left);
        }
        Map<String, Object> merged = new LinkedHashMap<>(left);
        merged.putAll(right);
        return Map.copyOf(merged);
    }

    private DegradationSummary collectDegradationSummary(List<AgentGraphMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return new DegradationSummary(false, List.of());
        }
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        for (AgentGraphMessage message : messages) {
            Map<String, Object> properties = message == null ? Map.of() : message.messageProperties();
            if (!Boolean.TRUE.equals(properties.get(DEGRADED_PROPERTY))) {
                continue;
            }
            Object reason = properties.get(DEGRADATION_REASON_PROPERTY);
            if (reason instanceof String value && !value.isBlank()) {
                reasons.add(value);
            } else {
                reasons.add("degraded");
            }
        }
        return new DegradationSummary(!reasons.isEmpty(), List.copyOf(reasons));
    }

    private String buildFallbackAssistantContent(InteractionState state, RuntimeException exception) {
        boolean chinese = prefersChinese(state);
        ChatRouteKind routeKind = state.routeKind().map(ChatRouteKind.class::cast).orElse(ChatRouteKind.CHAT);
        List<String> attemptedTools = attemptedToolNames(state.messages());
        List<ExecutionSource> sources = deduplicateSources(state.<ExecutionSource>sources());
        if (!sources.isEmpty()) {
            return chinese
                    ? "我已经完成了部分外部查询，但在整理最终回复时内部步骤失败。下面先附上已获取到的来源供你参考。"
                    : "I completed part of the external lookup, but an internal step failed while assembling the final reply. I am attaching the sources I already collected below.";
        }
        if (routeKind == ChatRouteKind.DOCUMENT_QA) {
            return chinese
                    ? "我已经尝试检索相关文档，但在整理最终回复时内部步骤失败，暂时没能生成稳定答复。请稍后重试。"
                    : "I was able to inspect the document flow, but an internal step failed while assembling the final reply. Please try again shortly.";
        }
        if (!attemptedTools.isEmpty()) {
            String toolSummary = String.join(", ", attemptedTools);
            return chinese
                    ? "我尝试调用工具（" + toolSummary + "）处理你的请求，但内部步骤失败，暂时没能生成稳定的最终答复。请稍后重试。"
                    : "I tried to use tools (" + toolSummary + ") to handle your request, but an internal step failed before I could produce a stable final answer. Please try again shortly.";
        }
        return chinese
                ? "我在处理你的请求时遇到了内部错误，暂时没能完成这次回复。请稍后重试。"
                : "I hit an internal error while working on your request and could not finish the reply. Please try again shortly.";
    }

    private List<String> attemptedToolNames(List<AgentGraphMessage> messages) {
        LinkedHashSet<String> toolNames = new LinkedHashSet<>();
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        for (AgentGraphMessage message : messages) {
            if (message.type() == AgentGraphMessageType.ASSISTANT && message.toolCalls() != null) {
                for (AgentGraphToolCall toolCall : message.toolCalls()) {
                    if (toolCall != null && toolCall.name() != null && !toolCall.name().isBlank()) {
                        toolNames.add(toolCall.name());
                    }
                }
            }
            if (message.type() == AgentGraphMessageType.TOOL && message.toolName() != null && !message.toolName().isBlank()) {
                toolNames.add(message.toolName());
            }
        }
        return List.copyOf(toolNames);
    }

    private boolean prefersChinese(InteractionState state) {
        PostMessageRequest request = state.<PostMessageRequest>request().orElse(null);
        String message = request == null || request.content() == null ? "" : request.content();
        return containsCjk(message);
    }

    private boolean containsCjk(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.codePoints().anyMatch(codePoint -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL;
        });
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Internal failure";
        }
        return exception.getMessage().trim();
    }

    private String renderPersistedUserContent(String content, List<ThreadFileReference> inputImages) {
        String trimmed = content == null ? "" : content.trim();
        if (inputImages == null || inputImages.isEmpty()) {
            return trimmed;
        }
        String imageSummary = inputImages.stream()
                .map(ThreadFileReference::name)
                .map(name -> name == null || name.isBlank() ? "image" : name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("image");
        if (trimmed.isBlank()) {
            return "[Attached images: " + imageSummary + "]";
        }
        return trimmed + "\n\n[Attached images: " + imageSummary + "]";
    }

    private String workflowFor(ChatRouteKind routeKind) {
        return switch (routeKind) {
            case DOCUMENT_QA -> "document-qa";
            case CHAT -> "chat";
            case RESEARCH_DRAFT -> "research-draft";
        };
    }

    private void logFlow(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }

    private record AgentStepContext(
            String providerId,
            AgentExecutionRequest request,
            String prompt,
            List<ToolDescriptor> availableTools
    ) {
    }

    private record DegradationSummary(boolean degraded, List<String> reasons) {
    }
}
