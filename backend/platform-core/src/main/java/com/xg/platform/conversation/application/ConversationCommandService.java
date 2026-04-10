package com.xg.platform.conversation.application;

import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.PostMessageRequest;
import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.contracts.shared.task.TaskStatus;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.conversation.runtime.InteractionState;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.shared.runtime.graph.PlatformGraphRunner;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.WorkspaceManager;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConversationCommandService {

    private static final Logger logger = Logger.getLogger(ConversationCommandService.class.getName());

    private final ThreadService threadService;
    private final TaskRepository taskRepository;
    private final RunEventRepository runEventRepository;
    private final MessageRepository messageRepository;
    private final WorkspaceManager workspaceManager;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final DocumentIngestService documentIngestService;
    private final PlatformGraphRunner platformGraphRunner;
    private final boolean logAgentFlow;

    public ConversationCommandService(ThreadService threadService,
                                      TaskRepository taskRepository,
                                      RunEventRepository runEventRepository,
                                      MessageRepository messageRepository,
                                      WorkspaceManager workspaceManager,
                                      AgentTurnExecutionSupport agentTurnExecutionSupport,
                                      DocumentIngestService documentIngestService,
                                      PlatformGraphRunner platformGraphRunner,
                                      boolean logAgentFlow) {
        this.threadService = threadService;
        this.taskRepository = taskRepository;
        this.runEventRepository = runEventRepository;
        this.messageRepository = messageRepository;
        this.workspaceManager = workspaceManager;
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.documentIngestService = documentIngestService;
        this.platformGraphRunner = platformGraphRunner;
        this.logAgentFlow = logAgentFlow;
    }

    public void executeMessage(String userId,
                               String threadId,
                               PostMessageRequest request,
                               Consumer<RunEvent> runEventConsumer) {
        PostMessageRequest normalizedRequest = normalizeMessageRequest(request);
        InteractionMode interactionMode = resolveInteractionMode(normalizedRequest);
        String runId = UUID.randomUUID().toString();
        logFlow(() -> "executeMessage thread=" + threadId
                + " run=" + runId
                + " mode=" + interactionMode
                + " provider=" + normalizedRequest.providerId()
                + " content=" + summarize(normalizedRequest.content()));
        prepareMessageExecution(userId, threadId, normalizedRequest);
        try {
            platformGraphRunner.invokeInteraction(Map.of(
                            InteractionState.USER_ID, userId,
                            InteractionState.THREAD_ID, threadId,
                            InteractionState.REQUEST, normalizedRequest,
                            InteractionState.RUN_ID, runId
                    ),
                    threadId,
                    runEventConsumer);
        } catch (RuntimeException exception) {
            publishEvent(userId, threadId, runId, RunEventType.MESSAGE_FAILED, Map.of(
                    "error", safeErrorMessage(exception)
            ), runEventConsumer);
            publishEvent(userId, threadId, runId, RunEventType.RUN_FAILED, Map.of(
                    "error", safeErrorMessage(exception)
            ), runEventConsumer);
            logFlow(() -> "executeMessage failed thread=" + threadId
                    + " run=" + runId
                    + " error=" + safeErrorMessage(exception));
            throw exception;
        } finally {
            threadService.touchThread(userId, threadId);
        }
    }

    public void prepareMessageExecution(String userId, String threadId, PostMessageRequest request) {
        PostMessageRequest normalizedRequest = normalizeMessageRequest(request);
        validateMessageRequest(normalizedRequest);
        String workspaceId = threadService.getThread(userId, threadId).workspaceId();
        workspaceManager.ensureThreadWorkspace(userId, threadId);
        agentTurnExecutionSupport.resolveProviderId(userId, normalizedRequest.providerId());
        validateSelectedDocuments(userId, workspaceId, normalizedRequest);
        if (resolveInteractionMode(normalizedRequest) == InteractionMode.DEEP_RESEARCH && hasActiveResearchTask(userId, threadId)) {
            throw new IllegalArgumentException("A research task is already running for this thread");
        }
    }

    public List<MessageRecord> listMessages(String userId, String threadId) {
        threadService.getThread(userId, threadId);
        return messageRepository.listMessages(userId, threadId);
    }

    private void validateMessageRequest(PostMessageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Message request must not be null");
        }
        boolean hasContent = request.content() != null && !request.content().isBlank();
        boolean hasImages = request.imageArtifactIds() != null && !request.imageArtifactIds().isEmpty();
        if (!hasContent && !hasImages) {
            throw new IllegalArgumentException("Message content must not be blank");
        }
    }

    private PostMessageRequest normalizeMessageRequest(PostMessageRequest request) {
        if (request == null || request.documentIds() == null || request.documentIds().isEmpty()) {
            return request;
        }
        List<String> normalizedDocumentIds = request.documentIds().stream()
                .filter(documentId -> documentId != null && !documentId.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
        if (normalizedDocumentIds.equals(request.documentIds())) {
            return request;
        }
        return new PostMessageRequest(
                request.content(),
                request.interactionMode(),
                request.providerId(),
                request.imageArtifactIds(),
                normalizedDocumentIds
        );
    }

    private void validateSelectedDocuments(String userId, String workspaceId, PostMessageRequest request) {
        if (request.documentIds() == null || request.documentIds().isEmpty()) {
            return;
        }
        Set<String> availableDocumentIds = documentIngestService.listDocumentsByWorkspace(userId, workspaceId).stream()
                .map(document -> document.documentId())
                .collect(java.util.stream.Collectors.toSet());
        for (String documentId : request.documentIds()) {
            if (!availableDocumentIds.contains(documentId)) {
                throw new IllegalArgumentException("Unknown document for this workspace: " + documentId);
            }
        }
    }

    private InteractionMode resolveInteractionMode(PostMessageRequest request) {
        return request.interactionMode() == null ? InteractionMode.CHAT : request.interactionMode();
    }

    private boolean hasActiveResearchTask(String userId, String threadId) {
        return taskRepository.listTasks(userId, threadId).stream()
                .anyMatch(task -> task.kind() == TaskKind.RESEARCH
                        && (task.status() == TaskStatus.QUEUED || task.status() == TaskStatus.RUNNING));
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

    private String summarize(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private void logFlow(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "The request could not be completed.";
        }
        return exception.getMessage().trim();
    }
}
