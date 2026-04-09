package com.xg.platform.agent.core.chat;

import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentExecutionResult;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.ConversationResponder;
import com.xg.platform.agent.core.ExecutionSource;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.message.RunEventType;
import com.xg.platform.contracts.message.ThreadFileReference;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.runtime.MemoryEventPayload;
import com.xg.platform.runtime.MemoryEventPublisher;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatFlowService {

    private static final Logger logger = Logger.getLogger(ChatFlowService.class.getName());

    private final ThreadRuntimeService threadRuntimeService;
    private final MessageRepository messageRepository;
    private final RunEventRepository runEventRepository;
    private final MemoryEventPublisher memoryEventPublisher;
    private final ArtifactService artifactService;
    private final WorkspaceManager workspaceManager;
    private final DocumentStore documentStore;
    private final ChatRouterService chatRouterService;
    private final ConversationResponder conversationResponder;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final boolean logAgentFlow;

    public ChatFlowService(ThreadRuntimeService threadRuntimeService,
                           MessageRepository messageRepository,
                           RunEventRepository runEventRepository,
                           MemoryEventPublisher memoryEventPublisher,
                           ArtifactService artifactService,
                           WorkspaceManager workspaceManager,
                           DocumentStore documentStore,
                           ChatRouterService chatRouterService,
                           ConversationResponder conversationResponder,
                           AgentTurnExecutionSupport agentTurnExecutionSupport,
                           boolean logAgentFlow) {
        this.threadRuntimeService = threadRuntimeService;
        this.messageRepository = messageRepository;
        this.runEventRepository = runEventRepository;
        this.memoryEventPublisher = memoryEventPublisher;
        this.artifactService = artifactService;
        this.workspaceManager = workspaceManager;
        this.documentStore = documentStore;
        this.chatRouterService = chatRouterService;
        this.conversationResponder = conversationResponder;
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.logAgentFlow = logAgentFlow;
    }

    public void execute(String userId,
                        String threadId,
                        PostMessageRequest request,
                        Consumer<RunEvent> runEventConsumer,
                        ThreadMemoryView memoryView,
                        String longTermMemory) {
        String runId = UUID.randomUUID().toString();
        List<DocumentRecord> documents = documentStore.listDocuments(userId, threadId);
        AgentExecutionRequest executionRequest = buildExecutionRequest(
                userId,
                threadId,
                runId,
                request,
                memoryView,
                longTermMemory,
                documents
        );
        MessageRecord userMessage = persistMessage(
                userId,
                threadId,
                MessageRole.USER,
                renderPersistedUserContent(request.content(), executionRequest.inputImages()),
                InteractionMode.CHAT,
                runId,
                null
        );
        publishEvent(userId, threadId, runId, RunEventType.MESSAGE_ACCEPTED, Map.of(
                "messageId", userMessage.messageId(),
                "interactionMode", InteractionMode.CHAT.name()
        ), runEventConsumer);
        ChatRouteDecision routeDecision = chatRouterService.route(executionRequest, documents);
        logFlow(() -> "handleChat thread=" + threadId
                + " run=" + runId
                + " responder=conversation-service"
                + " recentMessages=" + memoryView.recentMessages().size()
                + " route=" + routeDecision.routeKind());
        publishEvent(userId, threadId, runId, RunEventType.RUN_STARTED, Map.of(
                "providerId", resolvedProviderId(userId, request),
                "interactionMode", InteractionMode.CHAT.name(),
                "routeKind", routeDecision.routeKind().name(),
                "workflow", routeDecision.workflow(),
                "toolsEnabled", routeDecision.toolsEnabled()
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.AGENT_SELECTED, Map.of(
                "agentId", "conversation-service",
                "capability", "GENERAL"
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.ROUTE_SELECTED, Map.of(
                "interactionMode", InteractionMode.CHAT.name(),
                "routeKind", routeDecision.routeKind().name(),
                "workflow", routeDecision.workflow(),
                "toolsEnabled", routeDecision.toolsEnabled()
        ), runEventConsumer);

        try {
            List<ExecutionSource> observedVerifiedSources = new java.util.ArrayList<>();
            AgentExecutionResult result = conversationResponder.respond(
                    executionRequest,
                    new AgentOutputEmitter() {
                        @Override
                        public void emitText(String delta) {
                            if (delta == null || delta.isBlank()) {
                                return;
                            }
                            publishEvent(userId, threadId, runId, RunEventType.MESSAGE_DELTA, Map.of("delta", delta), runEventConsumer);
                        }

                        @Override
                        public void emitEvent(RunEventType eventType, Object payload) {
                            if (eventType == RunEventType.EVIDENCE_VERIFIED_ADDED) {
                                ExecutionSource source = toExecutionSource(payload, true);
                                if (source != null) {
                                    observedVerifiedSources.add(source);
                                }
                            }
                            publishEvent(userId, threadId, runId, eventType, payload, runEventConsumer);
                        }
                    }
            );

            MessageRecord assistantMessage = persistMessage(
                    userId,
                    threadId,
                    MessageRole.ASSISTANT,
                    result.finalContent(),
                    InteractionMode.CHAT,
                    runId,
                    null
            );
            publishEvent(userId, threadId, runId, RunEventType.MESSAGE_COMPLETED, Map.of(
                    "messageId", assistantMessage.messageId()
            ), runEventConsumer);
            publishMemoryEvent(RunEventType.MESSAGE_COMPLETED, userId, threadId, null, assistantMessage.messageId());
            List<ExecutionSource> completedSources = !result.sources().isEmpty()
                    ? result.sources()
                    : deduplicateSources(observedVerifiedSources);
            publishEvent(userId, threadId, runId, RunEventType.RUN_COMPLETED, Map.of(
                    "summary", safeSummary(result.summary(), result.finalContent()),
                    "providerId", result.providerId(),
                    "routeKind", (result.routeKind() == null ? routeDecision.routeKind() : result.routeKind()).name(),
                    "workflow", result.workflow() == null ? routeDecision.workflow() : result.workflow(),
                    "toolsEnabled", result.toolsEnabled() || routeDecision.toolsEnabled(),
                    "finalMessageId", assistantMessage.messageId(),
                    "sources", completedSources,
                    "sourceCount", completedSources.size(),
                    "usedVerifiedSources", result.usedVerifiedSources() == 0 ? completedSources.size() : result.usedVerifiedSources()
            ), runEventConsumer);
            logFlow(() -> "handleChat completed thread=" + threadId
                    + " run=" + runId
                    + " summary=" + safeSummary(result.summary(), result.finalContent()));
        } catch (RuntimeException exception) {
            publishEvent(userId, threadId, runId, RunEventType.MESSAGE_FAILED, Map.of(
                    "error", safeErrorMessage(exception)
            ), runEventConsumer);
            publishEvent(userId, threadId, runId, RunEventType.RUN_FAILED, Map.of(
                    "error", safeErrorMessage(exception)
            ), runEventConsumer);
            logFlow(() -> "handleChat failed thread=" + threadId
                    + " run=" + runId
                    + " error=" + safeErrorMessage(exception));
            throw exception;
        } finally {
            threadRuntimeService.touchThread(userId, threadId);
        }
    }

    private AgentExecutionRequest buildExecutionRequest(String userId,
                                                        String threadId,
                                                        String runId,
                                                        PostMessageRequest request,
                                                        ThreadMemoryView memoryView,
                                                        String longTermMemory,
                                                        List<DocumentRecord> documents) {
        List<ArtifactRecord> artifacts = artifactService.listArtifacts(userId, threadId);
        List<ThreadFileReference> uploadedFiles = artifacts.stream()
                .filter(artifact -> artifact.type() == ArtifactType.UPLOAD)
                .map(artifact -> new ThreadFileReference(
                        artifact.name(),
                        artifact.relativePath(),
                        artifactService.resolveArtifactPath(userId, artifact).toString(),
                        artifact.contentType(),
                        artifact.sizeBytes()
                ))
                .toList();
        List<ThreadFileReference> inputImages = resolveInputImages(userId, artifacts, request.imageArtifactIds());
        List<MessageRecord> recentMessages = memoryView.recentMessages();
        logFlow(() -> "buildExecutionRequest thread=" + threadId
                + " run=" + runId
                + " uploads=" + uploadedFiles.size()
                + " inputImages=" + inputImages.size()
                + " artifacts=" + artifacts.size()
                + " recentMessages=" + recentMessages.size());
        ChatRouteDecision routeDecision = chatRouterService.route(new AgentExecutionRequest(
                userId,
                threadId,
                runId,
                request.content(),
                null,
                request.providerId(),
                List.of(),
                List.of(),
                "auto",
                artifacts,
                uploadedFiles,
                inputImages,
                recentMessages,
                memoryView.summary(),
                longTermMemory,
                null,
                null,
                null,
                List.of(),
                request.documentIds()
        ), documents);
        return new AgentExecutionRequest(
                userId,
                threadId,
                runId,
                request.content(),
                null,
                request.providerId(),
                List.of(),
                List.of(),
                "auto",
                artifacts,
                uploadedFiles,
                inputImages,
                recentMessages,
                memoryView.summary(),
                longTermMemory,
                routeDecision.routeKind(),
                null,
                null,
                List.of(),
                request.documentIds()
        );
    }

    private List<ThreadFileReference> resolveInputImages(String userId,
                                                         List<ArtifactRecord> artifacts,
                                                         List<String> imageArtifactIds) {
        if (imageArtifactIds == null || imageArtifactIds.isEmpty()) {
            return List.of();
        }
        Map<String, ArtifactRecord> artifactsById = new LinkedHashMap<>();
        for (ArtifactRecord artifact : artifacts) {
            artifactsById.put(artifact.artifactId(), artifact);
        }
        Set<String> uniqueIds = new LinkedHashSet<>(imageArtifactIds);
        return uniqueIds.stream()
                .map(artifactId -> toInputImage(userId, artifactsById, artifactId))
                .toList();
    }

    private ThreadFileReference toInputImage(String userId,
                                             Map<String, ArtifactRecord> artifactsById,
                                             String artifactId) {
        ArtifactRecord artifact = artifactsById.get(artifactId);
        if (artifact == null) {
            throw new IllegalArgumentException("Image artifact not found in this thread: " + artifactId);
        }
        if (!isImageArtifact(artifact)) {
            throw new IllegalArgumentException("Artifact is not an image: " + artifact.name());
        }
        return new ThreadFileReference(
                artifact.name(),
                artifact.relativePath(),
                artifactService.resolveArtifactPath(userId, artifact).toString(),
                artifact.contentType(),
                artifact.sizeBytes()
        );
    }

    private boolean isImageArtifact(ArtifactRecord artifact) {
        return artifact.contentType() != null && artifact.contentType().toLowerCase().startsWith("image/");
    }

    private String renderPersistedUserContent(String content, List<ThreadFileReference> inputImages) {
        String trimmed = content == null ? "" : content.trim();
        if (inputImages == null || inputImages.isEmpty()) {
            return trimmed;
        }
        String imageSummary = inputImages.stream()
                .map(ThreadFileReference::name)
                .map(name -> name == null || name.isBlank() ? "image" : name)
                .collect(java.util.stream.Collectors.joining(", "));
        if (trimmed.isBlank()) {
            return "[Attached images: " + imageSummary + "]";
        }
        return trimmed + "\n\n[Attached images: " + imageSummary + "]";
    }

    private List<ExecutionSource> deduplicateSources(List<ExecutionSource> sources) {
        if (sources.isEmpty()) {
            return List.of();
        }
        Map<String, ExecutionSource> deduped = new LinkedHashMap<>();
        for (ExecutionSource source : sources) {
            deduped.putIfAbsent(source.url(), source);
        }
        return List.copyOf(deduped.values());
    }

    private ExecutionSource toExecutionSource(Object payload, boolean verified) {
        if (!(payload instanceof Map<?, ?> map)) {
            return null;
        }
        Object kind = map.get("kind");
        Object title = map.get("title");
        Object url = map.get("url");
        if (!(title instanceof String titleValue) || !(url instanceof String urlValue) || titleValue.isBlank() || urlValue.isBlank()) {
            return null;
        }
        Object domain = map.get("domain");
        return new ExecutionSource(
                kind instanceof String kindValue ? kindValue : (verified ? "WEB_PAGE" : "WEB_RESULT"),
                titleValue,
                domain instanceof String domainValue ? domainValue : "",
                urlValue,
                verified,
                verified
        );
    }

    private MessageRecord persistMessage(String userId,
                                         String threadId,
                                         MessageRole role,
                                         String content,
                                         InteractionMode interactionMode,
                                         String runId,
                                         String taskId) {
        MessageRecord messageRecord = messageRepository.append(userId, new MessageRecord(
                UUID.randomUUID().toString(),
                threadId,
                role,
                content,
                interactionMode,
                runId,
                taskId,
                Instant.now()
        ));
        return messageRecord;
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

    private String resolvedProviderId(String userId, PostMessageRequest request) {
        return agentTurnExecutionSupport.resolveProviderId(userId, request.providerId());
    }

    private String safeSummary(String summary, String fallbackContent) {
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        return summarize(fallbackContent);
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }

    private String summarize(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private void logFlow(Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }
}
