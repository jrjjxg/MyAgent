package com.xg.platform.conversation.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import com.xg.platform.skill.runtime.SkillRuntimeSupport;
import com.xg.platform.agent.core.ToolUseLimits;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.conversation.domain.ConversationRouteDecision;
import com.xg.platform.conversation.domain.ConversationRouteKind;
import com.xg.platform.conversation.application.ConversationRouterService;
import com.xg.platform.research.application.ResearchDraftScopingService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.workspace.ArtifactType;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.MessageRole;
import com.xg.platform.contracts.conversation.PostMessageRequest;
import com.xg.platform.contracts.research.ResearchDraftRecord;
import com.xg.platform.contracts.research.ResearchDraftStatus;
import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.research.runtime.ResearchScopingState;
import com.xg.platform.shared.runtime.graph.RunEventConsumerRegistry;
import com.xg.platform.document.application.ContextAssembler;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.document.domain.RetrievedChunk;
import com.xg.platform.memory.port.LongTermMemoryRepository;
import com.xg.platform.memory.port.MemoryEventPayload;
import com.xg.platform.memory.port.MemoryEventPublisher;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.bsc.langgraph4j.state.AppenderChannel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ConversationRuntimeSupport {

    private static final Logger logger = Logger.getLogger(ConversationRuntimeSupport.class.getName());
    private static final List<String> CHINA_LOCATION_HINTS = List.of(
            "china",
            "beijing",
            "tianjin",
            "shanghai",
            "guangzhou",
            "shenzhen",
            "hangzhou",
            "nanjing",
            "suzhou",
            "chengdu",
            "wuhan",
            "chongqing",
            "xian",
            "xi'an",
            "harbin",
            "qingdao",
            "dalian",
            "changsha",
            "ningbo",
            "xiamen",
            "fuzhou",
            "kunming",
            "jinan",
            "hefei",
            "zhengzhou",
            "shenyang"
    );
    private static final String MODEL_CONTEXT_PROPERTY = "modelContext";
    private static final String DEGRADED_PROPERTY = "degraded";
    private static final String DEGRADATION_REASON_PROPERTY = "degradationReason";
    private static final String DEGRADATION_ERROR_PROPERTY = "degradationError";

    private final ConversationMemoryService conversationMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final MemoryContextFormatter memoryContextFormatter;
    private final ResearchDraftRepository researchDraftRepository;
    private final ThreadService threadRuntimeService;
    private final ThreadMemorySnapshotRepository threadMemorySnapshotRepository;
    private final MessageRepository messageRepository;
    private final RunEventRepository runEventRepository;
    private final MemoryEventPublisher memoryEventPublisher;
    private final ArtifactService artifactService;
    private final WorkspaceManager workspaceManager;
    private final DocumentStore documentStore;
    private final ContextAssembler contextAssembler;
    private final DocumentIngestService documentIngestService;
    private final ConversationRouterService chatRouterService;
    private final AgentPromptService agentPromptService;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final AgentToolService agentToolService;
    private final SkillRegistry skillRegistry;
    private final ResearchDraftScopingService researchScopingFlowService;
    private final RunEventConsumerRegistry runEventConsumerRegistry;
    private final ObjectMapper objectMapper;
    private final ConversationDocumentSupport documentSupport;
    private final ConversationToolSupport toolSupport;
    private final ConversationEventSupport eventSupport;
    private final ConversationPersistenceSupport persistenceSupport;
    private final boolean logAgentFlow;
    private final int maxToolCalls;
    private final int maxSearchCalls;
    private final int maxFetchCalls;
    private final int minVerifiedSources;
    private final long timeoutMs;

    ConversationRuntimeSupport(ConversationMemoryService conversationMemoryService,
                               LongTermMemoryRepository longTermMemoryRepository,
                               MemoryContextFormatter memoryContextFormatter,
                               ResearchDraftRepository researchDraftRepository,
                               ThreadService threadRuntimeService,
                               ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                               MessageRepository messageRepository,
                               RunEventRepository runEventRepository,
                               MemoryEventPublisher memoryEventPublisher,
                               ArtifactService artifactService,
                               WorkspaceManager workspaceManager,
                               DocumentStore documentStore,
                               ContextAssembler contextAssembler,
                               DocumentIngestService documentIngestService,
                               ConversationRouterService chatRouterService,
                               AgentPromptService agentPromptService,
                               AgentTurnExecutionSupport agentTurnExecutionSupport,
                               AgentToolService agentToolService,
                               SkillRegistry skillRegistry,
                               ResearchDraftScopingService researchScopingFlowService,
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
        this.eventSupport = new ConversationEventSupport(runEventRepository, memoryEventPublisher);
        this.documentSupport = new ConversationDocumentSupport(
                artifactService,
                documentStore,
                contextAssembler,
                documentIngestService,
                objectMapper
        );
        this.toolSupport = new ConversationToolSupport(
                agentToolService,
                skillRegistry,
                eventSupport,
                logAgentFlow,
                timeoutMs
        );
        this.persistenceSupport = new ConversationPersistenceSupport(threadMemorySnapshotRepository, messageRepository);
        this.logAgentFlow = logAgentFlow;
        this.maxToolCalls = maxToolCalls;
        this.maxSearchCalls = maxSearchCalls;
        this.maxFetchCalls = maxFetchCalls;
        this.minVerifiedSources = minVerifiedSources;
        this.timeoutMs = timeoutMs;
    }

    public Map<String, Object> loadShortTermMemory(InteractionState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ThreadMemoryView memoryView = conversationMemoryService.threadMemoryView(userId, threadId);
        return Map.of(
                InteractionState.MEMORY_VIEW, memoryView,
                InteractionState.SESSION_SUMMARY, memoryView.summary()
        );
    }

    public Map<String, Object> loadLongTermMemory(InteractionState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        return Map.of(
                InteractionState.LONG_TERM_MEMORY,
                memoryContextFormatter.formatLongTermMemory(longTermMemoryRepository.listActive(userId), threadId)
        );
    }

    public Map<String, Object> loadDraftContext(InteractionState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ResearchDraftRecord draft = researchDraftRepository.findActiveDraft(userId, threadId).orElse(null);
        if (draft == null || draft.status() == ResearchDraftStatus.STARTED) {
            return Map.of();
        }
        return Map.of(InteractionState.CURRENT_DRAFT, draft);
    }

    public Map<String, Object> routeInteraction(InteractionState state) {
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Interaction request is missing"));
        InteractionMode interactionMode = request.interactionMode() == null
                ? InteractionMode.CHAT
                : request.interactionMode();
        if (interactionMode == InteractionMode.DEEP_RESEARCH) {
            return Map.of(
                    InteractionState.ROUTE_KIND, ConversationRouteKind.RESEARCH_DRAFT,
                    InteractionState.WORKFLOW, "research-draft",
                    InteractionState.TOOLS_ENABLED, false
            );
        }
        ConversationRouteDecision routeDecision = chatRouterService.route(buildRoutingRequest(state), documentStore.listDocuments(
                state.userId().orElseThrow(),
                state.threadId().orElseThrow()
        ));
        return Map.of(
                InteractionState.ROUTE_KIND, routeDecision.routeKind(),
                InteractionState.WORKFLOW, routeDecision.workflow(),
                InteractionState.TOOLS_ENABLED, routeDecision.toolsEnabled()
        );
    }

    public Map<String, Object> runScopingFrame(InteractionState state) {
        return runScopingFrame(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

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

    public Map<String, Object> persistDraft(InteractionState state) {
        return researchScopingFlowService.persistDraft(new ResearchScopingState(state.data()));
    }

    public Map<String, Object> persistAssistantMessage(InteractionState state) {
        return persistAssistantMessage(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    public Map<String, Object> persistAssistantMessage(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return researchScopingFlowService.persistAssistantMessage(new ResearchScopingState(state.data()), runEventConsumer);
    }

    public Map<String, Object> prepareAgentStep(InteractionState state) {
        return prepareAgentStep(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    public Map<String, Object> prepareAgentStep(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Interaction request is missing"));
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView().orElseThrow();
        ConversationRouteKind routeKind = state.routeKind().map(ConversationRouteKind.class::cast).orElseThrow();
        String runId = state.runId().orElseThrow();
        List<String> selectedDocumentIds = selectedDocumentIds(request);
        List<ArtifactRecord> artifacts = artifactService.listArtifacts(userId, threadId);
        List<ThreadFileReference> uploadedFiles = uploadedFiles(userId, artifacts);
        List<ThreadFileReference> inputImages = resolveInputImages(userId, artifacts, request);
        List<DocumentRecord> documents = maybeLoadDocuments(userId, threadId, routeKind, runId, selectedDocumentIds);
        List<RetrievedChunk> retrievedChunks = routeKind == ConversationRouteKind.DOCUMENT_QA
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
        ToolUseLimits toolUseLimits = routeKind == ConversationRouteKind.RESEARCH_DRAFT
                ? null
                : ToolUseLimits.fresh(maxToolCalls, maxSearchCalls, maxFetchCalls, 0, minVerifiedSources, timeoutMs);
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
                "agentId", routeKind == ConversationRouteKind.DOCUMENT_QA ? "docs-agent" : "general-agent",
                "capability", routeKind == ConversationRouteKind.DOCUMENT_QA ? "DOCS" : "GENERAL"
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

    public Map<String, Object> agent(InteractionState state) {
        AgentStepContext stepContext = buildAgentStepContext(state);
        AgentGraphMessage assistantMessage;
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String runId = state.runId().orElseThrow();
        Consumer<RunEvent> runEventConsumer = runEventConsumerRegistry.resolve(state.runContextKey().orElse(null));
        AgentOutputEmitter stepEventEmitter = eventSupport.stepEventEmitter(userId, threadId, runId, runEventConsumer);
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

    public Map<String, Object> executeTools(InteractionState state) {
        return executeTools(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

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
        boolean documentQaRoute = state.routeKind().map(ConversationRouteKind.class::cast).orElse(ConversationRouteKind.CHAT) == ConversationRouteKind.DOCUMENT_QA;
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
                documentPhase = documentSupport.determineDocumentPhase(documentPhase, toolCall.name(), scratchpad);
                documentSearchHints = new ArrayList<>(documentSupport.buildDocumentSearchHints(question, scratchpad, knownSectionTitles));
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

    public Map<String, Object> persistTurnArtifacts(InteractionState state) {
        return persistTurnArtifacts(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    public Map<String, Object> persistTurnArtifacts(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        if (state.routeKind().map(ConversationRouteKind.class::cast).orElse(ConversationRouteKind.CHAT) == ConversationRouteKind.RESEARCH_DRAFT) {
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

    public Map<String, Object> publishTurnEvents(InteractionState state) {
        return publishTurnEvents(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    public Map<String, Object> publishTurnEvents(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        if (state.routeKind().map(ConversationRouteKind.class::cast).orElse(ConversationRouteKind.CHAT) == ConversationRouteKind.RESEARCH_DRAFT) {
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
        ConversationRouteKind routeKind = state.routeKind().map(ConversationRouteKind.class::cast).orElse(ConversationRouteKind.CHAT);
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
        return AgentExecutionRequest.builder()
                .userId(userId)
                .threadId(threadId)
                .runId(UUID.randomUUID().toString())
                .message(request.content())
                .providerId(request.providerId())
                .requestedCapabilities(List.of())
                .skillIds(List.of())
                .skillSelectionMode("auto")
                .artifacts(artifacts)
                .uploadedFiles(uploadedFiles(userId, artifacts))
                .inputImages(resolveInputImages(userId, artifacts, request))
                .recentMessages(memoryView.recentMessages())
                .sessionSummary(state.sessionSummary().orElse(""))
                .longTermMemory(state.longTermMemory().orElse(""))
                .activeSkillIds(List.of())
                .selectedDocumentIds(selectedDocumentIds(request))
                .build();
    }

    private AgentStepContext buildAgentStepContext(InteractionState state) {
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Interaction request is missing"));
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ConversationRouteKind routeKind = state.routeKind().map(ConversationRouteKind.class::cast).orElseThrow();
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView().orElseThrow();
        List<ArtifactRecord> artifacts = state.<ArtifactRecord>artifacts();
        List<ThreadFileReference> uploadedFiles = state.<ThreadFileReference>uploadedFiles();
        List<ThreadFileReference> inputImages = state.<ThreadFileReference>inputImages();
        ToolUseLimits toolUseLimits = state.<ToolUseLimits>toolUseLimits().orElse(null);
        List<String> selectedDocumentIds = selectedDocumentIds(request);
        AgentExecutionRequest executionRequest = AgentExecutionRequest.builder()
                .userId(userId)
                .threadId(threadId)
                .runId(state.runId().orElseThrow())
                .message(request.content())
                .agentId(routeKind == ConversationRouteKind.DOCUMENT_QA ? "docs-agent" : "general-agent")
                .providerId(request.providerId())
                .requestedCapabilities(List.of())
                .skillIds(List.of())
                .skillSelectionMode("auto")
                .artifacts(artifacts)
                .uploadedFiles(uploadedFiles)
                .inputImages(inputImages)
                .recentMessages(memoryView.recentMessages())
                .sessionSummary(state.sessionSummary().orElse(""))
                .longTermMemory(state.longTermMemory().orElse(""))
                .chatRouteKind(routeKind)
                .toolUseLimits(toolUseLimits)
                .activeSkillIds(state.activeSkillIds())
                .selectedDocumentIds(selectedDocumentIds)
                .build();
        List<ToolDescriptor> availableTools = state.<ToolDescriptor>availableTools();
        List<SkillDescriptor> availableSkills = state.<SkillDescriptor>availableSkills();
        List<com.xg.platform.skill.domain.SkillDefinition> loadedSkills = resolveLoadedSkills(userId, state.activeSkillIds());
        List<ToolDescriptor> prioritizedTools = SkillRuntimeSupport.prioritizeToolsForSkills(availableTools, loadedSkills);
        String currentPhase = routeKind == ConversationRouteKind.DOCUMENT_QA
                ? state.documentPhase().orElse("PLAN")
                : "SYNTHESIZE";
        String workingMemory = routeKind == ConversationRouteKind.DOCUMENT_QA
                ? state.documentWorkingMemory().orElse("")
                : "";
        String readingPlan = routeKind == ConversationRouteKind.DOCUMENT_QA
                ? state.documentReadingPlan().orElse("")
                : "";
        List<String> searchHints = routeKind == ConversationRouteKind.DOCUMENT_QA
                ? state.documentSearchHints()
                : List.of();
        String prompt = agentPromptService.renderPrompt(new AgentPromptRequest(
                routeKind == ConversationRouteKind.DOCUMENT_QA ? "docs-agent" : "general-agent",
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
                                                    ConversationRouteKind routeKind,
                                                    String runId,
                                                    List<String> selectedDocumentIds) {
        return documentSupport.maybeLoadDocuments(userId, threadId, routeKind, runId, selectedDocumentIds);
    }

    private Map<String, Object> initializeDocumentQaState(String userId,
                                                          String question,
                                                          ConversationRouteKind routeKind,
                                                          List<DocumentRecord> documents) {
        return documentSupport.initializeDocumentQaState(userId, question, routeKind, documents);
    }

    private List<RetrievedChunk> retrieveChunks(String userId, String query, List<DocumentRecord> documents) {
        return documentSupport.retrieveChunks(userId, query, documents);
    }

    private List<String> knownSectionTitles(String userId, List<DocumentRecord> documents) {
        return documentSupport.knownSectionTitles(userId, documents);
    }

    private DocumentQaScratchpad defaultDocumentScratchpad(String question, List<DocumentRecord> documents) {
        return documentSupport.defaultDocumentScratchpad(question, documents);
    }

    private DocumentQaScratchpad updateDocumentScratchpad(String question,
                                                          DocumentQaScratchpad scratchpad,
                                                          String toolName,
                                                          JsonNode toolOutput,
                                                          List<String> knownSectionTitles) {
        return documentSupport.updateDocumentScratchpad(question, scratchpad, toolName, toolOutput, knownSectionTitles);
    }

    private String compressDocumentToolOutput(String toolName, JsonNode output) {
        return documentSupport.compressDocumentToolOutput(toolName, output);
    }

    private List<ToolDescriptor> availableTools(String userId) {
        return List.copyOf(agentToolService.listAvailableTools(userId));
    }

    private List<ThreadFileReference> uploadedFiles(String userId, List<ArtifactRecord> artifacts) {
        return documentSupport.uploadedFiles(userId, artifacts);
    }

    private List<ThreadFileReference> resolveInputImages(String userId, List<ArtifactRecord> artifacts, PostMessageRequest request) {
        return documentSupport.resolveInputImages(userId, artifacts, request);
    }

    private JsonNode executeToolCall(InteractionState state,
                                     Consumer<RunEvent> runEventConsumer,
                                     ToolUseLimits toolUseLimits,
                                     ToolDescriptor tool,
                                     AgentGraphToolCall toolCall,
                                     List<ExecutionSource> sources,
                                     List<String> activeSkillIds) {
        return toolSupport.executeToolCall(
                state,
                runEventConsumer,
                toolUseLimits,
                tool,
                toolCall,
                sources,
                activeSkillIds,
                selectedDocumentIds(state.<PostMessageRequest>request().orElse(null))
        );
    }

    private List<com.xg.platform.skill.domain.SkillDefinition> resolveLoadedSkills(String userId,
                                                                            List<String> activeSkillIds) {
        return toolSupport.resolveLoadedSkills(userId, activeSkillIds);
    }

    private List<String> selectedDocumentIds(PostMessageRequest request) {
        return persistenceSupport.selectedDocumentIds(request);
    }

    private List<String> loadPersistedActiveSkillIds(String userId, String threadId) {
        return persistenceSupport.loadPersistedActiveSkillIds(userId, threadId);
    }

    private void persistActiveSkillIds(String userId, String threadId, List<String> activeSkillIds) {
        persistenceSupport.persistActiveSkillIds(userId, threadId, activeSkillIds);
    }

    private List<ExecutionSource> deduplicateSources(List<ExecutionSource> sources) {
        return persistenceSupport.deduplicateSources(sources);
    }

    private AgentGraphMessage findLastFinalAssistantMessage(List<AgentGraphMessage> messages) {
        return persistenceSupport.findLastFinalAssistantMessage(messages);
    }

    private String appendSourceAppendix(String response, List<ExecutionSource> sources) {
        return persistenceSupport.appendSourceAppendix(response, sources);
    }

    private MessageRecord persistMessage(String userId,
                                         String threadId,
                                         MessageRole role,
                                         String content,
                                         InteractionMode interactionMode,
                                         String runId,
                                         String taskId) {
        return persistenceSupport.persistMessage(userId, threadId, role, content, interactionMode, runId, taskId);
    }

    private void publishEvent(String userId,
                              String threadId,
                              String runId,
                              RunEventType runEventType,
                              Object payload,
                              Consumer<RunEvent> runEventConsumer) {
        eventSupport.publishEvent(userId, threadId, runId, runEventType, payload, runEventConsumer);
    }

    private void publishMemoryEvent(RunEventType eventType,
                                    String userId,
                                    String threadId,
                                    String taskId,
                                    String messageId) {
        eventSupport.publishMemoryEvent(eventType, userId, threadId, taskId, messageId);
    }

    private Iterable<String> split(String response, int chunkSize) {
        return eventSupport.split(response, chunkSize);
    }

    private String summarize(String content) {
        return eventSupport.summarize(content);
    }

    private JsonNode toolFailurePayload(String toolName, String errorMessage) {
        return JsonNodeFactory.instance.objectNode()
                .put("status", "error")
                .put("toolName", toolName == null ? "" : toolName)
                .put("error", errorMessage == null || errorMessage.isBlank() ? "Tool execution failed" : errorMessage);
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
        ConversationDegradationSummary summary = persistenceSupport.collectDegradationSummary(messages);
        return new DegradationSummary(summary.degraded(), summary.reasons());
    }

    private String buildFallbackAssistantContent(InteractionState state, RuntimeException exception) {
        boolean chinese = prefersChinese(state);
        ConversationRouteKind routeKind = state.routeKind().map(ConversationRouteKind.class::cast).orElse(ConversationRouteKind.CHAT);
        List<String> attemptedTools = attemptedToolNames(state.messages());
        List<ExecutionSource> sources = deduplicateSources(state.<ExecutionSource>sources());
        if (!sources.isEmpty()) {
            return chinese
                    ? "我已经完成了部分外部查询，但在整理最终回复时内部步骤失败。下面先附上已获取到的来源供你参考。"
                    : "I completed part of the external lookup, but an internal step failed while assembling the final reply. I am attaching the sources I already collected below.";
        }
        if (routeKind == ConversationRouteKind.DOCUMENT_QA) {
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
        return containsCjk(message) || containsChinaLocationHint(message);
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

    private boolean containsChinaLocationHint(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String hint : CHINA_LOCATION_HINTS) {
            if (normalized.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Internal failure";
        }
        return exception.getMessage().trim();
    }

    private String renderPersistedUserContent(String content, List<ThreadFileReference> inputImages) {
        return persistenceSupport.renderPersistedUserContent(content, inputImages);
    }

    private String workflowFor(ConversationRouteKind routeKind) {
        return persistenceSupport.workflowFor(routeKind);
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
