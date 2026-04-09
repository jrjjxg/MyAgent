package com.xg.platform.agent.core.research.execution;

import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.task.TaskRecord;
import com.xg.platform.contracts.task.TaskStatus;
import com.xg.platform.graph.ResearchGraphNodes;
import com.xg.platform.graph.ResearchTaskState;
import com.xg.platform.runtime.LongTermMemoryRepository;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.ThreadRuntimeService;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

public class ResearchExecutionGraphNodeService implements ResearchGraphNodes {

    private final TaskRepository taskRepository;
    private final ThreadRuntimeService threadRuntimeService;
    private final ConversationMemoryService conversationMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final MemoryContextFormatter memoryContextFormatter;
    private final ResearchExecutionFlowService researchExecutionFlowService;

    public ResearchExecutionGraphNodeService(TaskRepository taskRepository,
                                             ThreadRuntimeService threadRuntimeService,
                                             ConversationMemoryService conversationMemoryService,
                                             LongTermMemoryRepository longTermMemoryRepository,
                                             MemoryContextFormatter memoryContextFormatter,
                                             ResearchExecutionFlowService researchExecutionFlowService) {
        this.taskRepository = taskRepository;
        this.threadRuntimeService = threadRuntimeService;
        this.conversationMemoryService = conversationMemoryService;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.memoryContextFormatter = memoryContextFormatter;
        this.researchExecutionFlowService = researchExecutionFlowService;
    }

    @Override
    public Map<String, Object> hydrateTask(ResearchTaskState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        TaskRecord task = taskRepository.findTask(userId, threadId, taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
        if (task.status() == TaskStatus.CANCELLED) {
            return Map.of(
                    ResearchTaskState.SKIP_EXECUTION, true,
                    ResearchTaskState.RESULT, "cancelled"
            );
        }
        String researchBrief = state.taskInput()
                .filter(input -> !input.isBlank())
                .orElse(task.summary());
        ThreadMemoryView memoryView = conversationMemoryService.threadMemoryView(userId, threadId);
        return executeStage(state, current -> Map.of(
                ResearchTaskState.RESEARCH_BRIEF, researchBrief,
                ResearchTaskState.MEMORY_VIEW, memoryView,
                ResearchTaskState.SESSION_SUMMARY, memoryView.summary(),
                ResearchTaskState.LONG_TERM_MEMORY,
                memoryContextFormatter.formatLongTermMemory(longTermMemoryRepository.listActive(userId), threadId),
                ResearchTaskState.CURRENT_STAGE, "plan"
        ));
    }

    @Override
    public Map<String, Object> normalizePlan(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::normalizePlan);
    }

    @Override
    public Map<String, Object> initializeSession(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::initializeSession);
    }

    @Override
    public Map<String, Object> planAgenda(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::planAgenda);
    }

    @Override
    public Map<String, Object> discoverySearch(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::discoverySearch);
    }

    @Override
    public Map<String, Object> intermediateSynthesis(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::intermediateSynthesis);
    }

    @Override
    public Map<String, Object> gapAnalysis(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::gapAnalysis);
    }

    @Override
    public Map<String, Object> routeIteration(ResearchTaskState state) {
        return Map.of(
                ResearchTaskState.CURRENT_STAGE,
                state.currentStage().orElse("gap_analysis")
        );
    }

    @Override
    public Map<String, Object> focusedFollowup(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::focusedFollowup);
    }

    @Override
    public Map<String, Object> convergeFinalize(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::convergeFinalize);
    }

    @Override
    public Map<String, Object> writeArtifacts(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::writeArtifacts);
    }

    @Override
    public Map<String, Object> markTaskCompleted(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::markTaskCompleted);
    }

    @Override
    public Map<String, Object> publishCompletionEvents(ResearchTaskState state) {
        return executeStage(state, researchExecutionFlowService::publishCompletionEvents);
    }

    private Map<String, Object> executeStage(ResearchTaskState state,
                                             Function<ResearchTaskState, Map<String, Object>> stageAction) {
        try {
            return stageAction.apply(state);
        } catch (RuntimeException exception) {
            researchExecutionFlowService.markFailed(state, exception);
            throw exception;
        } finally {
            if (state.userId().isPresent() && state.threadId().isPresent()) {
                threadRuntimeService.touchThread(state.userId().orElseThrow(), state.threadId().orElseThrow());
            }
        }
    }
}
