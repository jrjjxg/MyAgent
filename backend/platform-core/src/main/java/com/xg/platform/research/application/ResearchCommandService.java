package com.xg.platform.research.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.research.ApprovedResearchPlan;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.MessageRole;
import com.xg.platform.contracts.research.ResearchDraftRecord;
import com.xg.platform.contracts.research.ResearchDraftStatus;
import com.xg.platform.contracts.research.ResearchPlanStep;
import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.contracts.research.StartResearchRequest;
import com.xg.platform.contracts.research.UpdateResearchDraftRequest;
import com.xg.platform.contracts.research.UpdateResearchTaskRequest;
import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.contracts.shared.task.TaskRecord;
import com.xg.platform.contracts.shared.task.TaskStatus;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.memory.port.MemoryEventPayload;
import com.xg.platform.memory.port.MemoryEventPublisher;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.research.port.ResearchTaskSnapshotRepository;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.shared.runtime.async.TaskDispatchRequest;
import com.xg.platform.shared.runtime.async.TaskDispatcher;
import com.xg.platform.workspace.application.ThreadService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResearchCommandService {

    private static final Logger logger = Logger.getLogger(ResearchCommandService.class.getName());

    private final ThreadService threadService;
    private final TaskRepository taskRepository;
    private final RunEventRepository runEventRepository;
    private final MemoryEventPublisher memoryEventPublisher;
    private final MessageRepository messageRepository;
    private final ResearchDraftRepository researchDraftRepository;
    private final ResearchTaskSnapshotRepository researchTaskSnapshotRepository;
    private final TaskDispatcher taskDispatcher;
    private final ObjectMapper objectMapper;
    private final boolean logAgentFlow;

    public ResearchCommandService(ThreadService threadService,
                                  TaskRepository taskRepository,
                                  RunEventRepository runEventRepository,
                                  MemoryEventPublisher memoryEventPublisher,
                                  MessageRepository messageRepository,
                                  ResearchDraftRepository researchDraftRepository,
                                  ResearchTaskSnapshotRepository researchTaskSnapshotRepository,
                                  TaskDispatcher taskDispatcher,
                                  ObjectMapper objectMapper,
                                  boolean logAgentFlow) {
        this.threadService = threadService;
        this.taskRepository = taskRepository;
        this.runEventRepository = runEventRepository;
        this.memoryEventPublisher = memoryEventPublisher;
        this.messageRepository = messageRepository;
        this.researchDraftRepository = researchDraftRepository;
        this.researchTaskSnapshotRepository = researchTaskSnapshotRepository;
        this.taskDispatcher = taskDispatcher;
        this.objectMapper = objectMapper;
        this.logAgentFlow = logAgentFlow;
    }

    public ResearchDraftRecord getActiveDraft(String userId, String threadId) {
        threadService.getThread(userId, threadId);
        ResearchDraftRecord draft = researchDraftRepository.findActiveDraft(userId, threadId).orElse(null);
        if (draft != null && draft.status() == ResearchDraftStatus.STARTED) {
            return null;
        }
        return draft;
    }

    public void discardDraft(String userId, String threadId) {
        threadService.getThread(userId, threadId);
        researchDraftRepository.clear(userId, threadId);
    }

    public ResearchDraftRecord updateResearchDraft(String userId, String threadId, UpdateResearchDraftRequest request) {
        threadService.getThread(userId, threadId);
        if (request == null) {
            throw new IllegalArgumentException("Research draft update must not be null");
        }
        ResearchDraftRecord draft = researchDraftRepository.findActiveDraft(userId, threadId)
                .orElseThrow(() -> new NoSuchElementException("No active research draft"));
        if (draft.status() == ResearchDraftStatus.STARTED) {
            throw new IllegalStateException("Research draft has already been started");
        }
        int requestedRevision = request.revision() == null ? -1 : request.revision();
        if (requestedRevision != draft.revision()) {
            throw new IllegalStateException("Research plan is stale. Refresh the draft before editing.");
        }

        String title = mergeText(request.title(), draft.title());
        String brief = mergeText(request.brief(), draft.brief());
        String objective = mergeOptionalText(request.objective(), draft.objective());
        String scope = mergeOptionalText(request.scope(), draft.scope());
        String outputFormat = mergeOptionalText(request.outputFormat(), draft.outputFormat());
        List<String> constraints = normalizeStrings(request.constraints(), draft.constraints());
        List<String> questions = normalizeStrings(request.questions(), draft.questions());
        String planSummary = mergeOptionalText(request.planSummary(), draft.planSummary());
        List<ResearchPlanStep> planSteps = normalizePlanSteps(request.planSteps(), draft.planSteps());
        boolean ready = isDraftReady(title, brief, planSummary, planSteps);

        ResearchDraftRecord updatedDraft = new ResearchDraftRecord(
                draft.draftId(),
                draft.threadId(),
                ready ? ResearchDraftStatus.READY : ResearchDraftStatus.COLLECTING,
                title,
                brief,
                objective,
                scope,
                outputFormat,
                constraints,
                questions,
                draft.revision() + 1,
                planSummary,
                planSteps,
                ready,
                draft.lastUserMessageId(),
                draft.lastAssistantMessageId(),
                draft.createdAt(),
                Instant.now()
        );
        researchDraftRepository.save(userId, updatedDraft);
        publishEvent(userId, threadId, updatedDraft.draftId(), RunEventType.RESEARCH_PLAN_PREVIEW_UPDATED, updatedDraft, event -> {
        });
        publishEvent(userId, threadId, updatedDraft.draftId(), RunEventType.RESEARCH_BRIEF_UPDATED, updatedDraft, event -> {
        });
        return updatedDraft;
    }

    public TaskRecord startResearch(String userId, String threadId, StartResearchRequest request) {
        var thread = threadService.getThread(userId, threadId);
        if (hasActiveResearchTask(userId, threadId)) {
            throw new IllegalArgumentException("Only one active research task is allowed per thread");
        }
        ResearchDraftRecord draft = researchDraftRepository.findActiveDraft(userId, threadId)
                .orElseThrow(() -> new NoSuchElementException("No active research draft"));
        if (!draft.ready()) {
            throw new IllegalStateException("Research brief is not ready yet");
        }
        int requestedRevision = request == null || request.draftRevision() == null ? -1 : request.draftRevision();
        if (requestedRevision != draft.revision()) {
            throw new IllegalStateException("Research plan is stale. Refresh the draft before starting research.");
        }
        ApprovedResearchPlan approvedPlan = buildApprovedPlan(draft);

        String taskId = UUID.randomUUID().toString();
        TaskRecord task = taskRepository.createQueuedTask(
                userId,
                thread.workspaceId(),
                threadId,
                null,
                taskId,
                "general-agent",
                TaskKind.RESEARCH,
                draft.title(),
                summarize(draft.brief()),
                draft.draftId(),
                3
        );
        logFlow(() -> "startResearch thread=" + threadId
                + " task=" + task.taskId()
                + " title=" + summarize(task.title())
                + " brief=" + summarize(draft.brief()));

        researchDraftRepository.clear(userId, threadId);
        researchTaskSnapshotRepository.deleteByTask(userId, taskId);
        publishEvent(userId, threadId, taskId, RunEventType.TASK_CREATED, Map.of(
                "taskId", task.taskId(),
                "kind", task.kind().name(),
                "title", task.title()
        ), event -> {
        });
        publishMemoryEvent(RunEventType.TASK_CREATED, userId, threadId, taskId, null);
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_PLAN_APPROVED, approvedPlan, event -> {
        });
        taskDispatcher.dispatch(new TaskDispatchRequest(
                userId,
                threadId,
                taskId,
                TaskKind.RESEARCH,
                request == null ? null : request.providerId(),
                serializeApprovedPlan(approvedPlan),
                thread.workspaceId()
        ));
        threadService.touchThread(userId, threadId);
        return task;
    }

    public TaskRecord updateResearchTask(String userId, String threadId, String taskId, UpdateResearchTaskRequest request) {
        threadService.getThread(userId, threadId);
        TaskRecord task = taskRepository.findTask(userId, threadId, taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
        if (task.kind() != TaskKind.RESEARCH) {
            throw new IllegalArgumentException("Only research tasks accept updates");
        }
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("Task update content must not be blank");
        }
        logFlow(() -> "updateResearchTask thread=" + threadId
                + " task=" + taskId
                + " stage=" + task.stage()
                + " status=" + task.status()
                + " refinement=" + summarize(request.content()));
        MessageRecord refinementMessage = messageRepository.append(userId, new MessageRecord(
                UUID.randomUUID().toString(),
                threadId,
                MessageRole.USER,
                request.content().trim(),
                InteractionMode.DEEP_RESEARCH,
                null,
                taskId,
                Instant.now()
        ));
        TaskRecord updated = taskRepository.updateTask(
                userId,
                threadId,
                taskId,
                null,
                task.title(),
                task.status() == TaskStatus.RUNNING && isLateResearchStage(task.stage())
                        ? "Late research update recorded for follow-up: " + summarize(request.content())
                        : "Research refinement accepted: " + summarize(request.content()),
                task.stage(),
                task.progress(),
                task.linkedDraftId(),
                task.resultArtifactId()
        );
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_ACTIVITY, Map.of(
                "taskId", taskId,
                "summary", task.status() == TaskStatus.RUNNING && isLateResearchStage(task.stage())
                        ? "Recorded a follow-up request for after the current research pass."
                        : "Accepted a refinement and will incorporate it into the remaining research steps.",
                "kind", "task-update"
        ), event -> {
        });
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_BRIEF_UPDATED, Map.of(
                "taskId", taskId,
                "refinement", request.content().trim(),
                "replanEligible", task.status() == TaskStatus.QUEUED || !isLateResearchStage(task.stage())
        ), event -> {
        });
        publishMemoryEvent(RunEventType.RESEARCH_BRIEF_UPDATED, userId, threadId, taskId, refinementMessage.messageId());
        return updated;
    }

    public TaskRecord cancelResearchTask(String userId, String threadId, String taskId) {
        threadService.getThread(userId, threadId);
        TaskRecord task = taskRepository.findTask(userId, threadId, taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
        TaskRecord cancelled = taskRepository.updateTask(
                userId,
                threadId,
                taskId,
                TaskStatus.CANCELLED,
                task.title(),
                task.summary(),
                "cancelled",
                task.progress(),
                task.linkedDraftId(),
                task.resultArtifactId()
        );
        publishEvent(userId, threadId, taskId, RunEventType.TASK_CANCELLED, Map.of("taskId", taskId), event -> {
        });
        publishMemoryEvent(RunEventType.TASK_CANCELLED, userId, threadId, taskId, null);
        logFlow(() -> "cancelResearchTask thread=" + threadId + " task=" + taskId);
        return cancelled;
    }

    private boolean hasActiveResearchTask(String userId, String threadId) {
        return taskRepository.listTasks(userId, threadId).stream()
                .anyMatch(task -> task.kind() == TaskKind.RESEARCH
                        && (task.status() == TaskStatus.QUEUED || task.status() == TaskStatus.RUNNING));
    }

    private boolean isDraftReady(String title,
                                 String brief,
                                 String planSummary,
                                 List<ResearchPlanStep> planSteps) {
        return hasText(title)
                && hasText(brief)
                && hasText(planSummary)
                && planSteps.stream().anyMatch(step -> hasText(step.title()) && hasText(step.query()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String mergeText(String value, String fallback) {
        String merged = mergeOptionalText(value, fallback);
        return merged == null ? "" : merged;
    }

    private String mergeOptionalText(String value, String fallback) {
        if (value != null) {
            return value.trim();
        }
        return fallback == null ? null : fallback.trim();
    }

    private List<String> normalizeStrings(List<String> values, List<String> fallback) {
        List<String> source = values != null ? values : fallback;
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<ResearchPlanStep> normalizePlanSteps(List<ResearchPlanStep> values, List<ResearchPlanStep> fallback) {
        List<ResearchPlanStep> source = values != null ? values : fallback;
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<ResearchPlanStep> normalized = new java.util.ArrayList<>();
        int index = 0;
        for (ResearchPlanStep step : source) {
            if (step == null) {
                continue;
            }
            String title = step.title() == null ? "" : step.title().trim();
            String objective = step.objective() == null ? title : step.objective().trim();
            String query = step.query() == null ? title : step.query().trim();
            String outputFocus = step.outputFocus() == null ? "" : step.outputFocus().trim();
            if (title.isBlank() && query.isBlank()) {
                continue;
            }
            normalized.add(new ResearchPlanStep(
                    hasText(step.stepId()) ? step.stepId().trim() : "step-" + (++index),
                    title.isBlank() ? "Step " + (index == 0 ? normalized.size() + 1 : index) : title,
                    objective,
                    query.isBlank() ? title : query,
                    step.useWeb(),
                    step.useDocuments(),
                    outputFocus
            ));
        }
        return List.copyOf(normalized);
    }

    private ApprovedResearchPlan buildApprovedPlan(ResearchDraftRecord draft) {
        return new ApprovedResearchPlan(
                draft.draftId(),
                draft.revision(),
                draft.title(),
                draft.brief(),
                draft.objective(),
                draft.scope(),
                draft.outputFormat(),
                draft.constraints(),
                draft.planSummary(),
                draft.planSteps()
        );
    }

    private String serializeApprovedPlan(ApprovedResearchPlan approvedPlan) {
        try {
            return objectMapper.writeValueAsString(approvedPlan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize approved research plan", exception);
        }
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

    private String summarize(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private boolean isLateResearchStage(String stage) {
        if (stage == null) {
            return false;
        }
        return "compression".equalsIgnoreCase(stage)
                || "report".equalsIgnoreCase(stage)
                || "completed".equalsIgnoreCase(stage)
                || "failed".equalsIgnoreCase(stage);
    }

    private void logFlow(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }
}
