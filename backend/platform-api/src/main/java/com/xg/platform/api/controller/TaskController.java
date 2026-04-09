package com.xg.platform.api.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.agent.core.AgentExecutionService;
import com.xg.platform.contracts.message.UpdateResearchTaskRequest;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.research.ResearchFindingRecord;
import com.xg.platform.contracts.research.ResearchIterationRecord;
import com.xg.platform.contracts.research.ResearchReportSection;
import com.xg.platform.contracts.research.ResearchReportView;
import com.xg.platform.contracts.research.ResearchSourceRecord;
import com.xg.platform.api.service.TaskResearchReadService;
import com.xg.platform.contracts.task.TaskRecord;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/threads/{threadId}/tasks")
public class TaskController {

    private final ThreadRuntimeService threadRuntimeService;
    private final TaskRepository taskRepository;
    private final AgentExecutionService agentExecutionService;
    private final TaskResearchReadService taskResearchReadService;

    public TaskController(ThreadRuntimeService threadRuntimeService,
                          TaskRepository taskRepository,
                          AgentExecutionService agentExecutionService,
                          TaskResearchReadService taskResearchReadService) {
        this.threadRuntimeService = threadRuntimeService;
        this.taskRepository = taskRepository;
        this.agentExecutionService = agentExecutionService;
        this.taskResearchReadService = taskResearchReadService;
    }

    @GetMapping
    public List<TaskRecord> listTasks(@CurrentUserId String userId,
                                      @PathVariable String threadId) {
        threadRuntimeService.getThread(userId, threadId);
        return taskRepository.listTasks(userId, threadId);
    }

    @GetMapping("/{taskId}")
    public TaskRecord getTask(@CurrentUserId String userId,
                              @PathVariable String threadId,
                              @PathVariable String taskId) {
        threadRuntimeService.getThread(userId, threadId);
        return taskRepository.findTask(userId, threadId, taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
    }

    @PostMapping("/{taskId}/updates")
    public TaskRecord updateTask(@CurrentUserId String userId,
                                 @PathVariable String threadId,
                                 @PathVariable String taskId,
                                 @RequestBody UpdateResearchTaskRequest request) {
        return agentExecutionService.updateResearchTask(userId, threadId, taskId, request);
    }

    @PostMapping("/{taskId}/cancel")
    public TaskRecord cancelTask(@CurrentUserId String userId,
                                 @PathVariable String threadId,
                                 @PathVariable String taskId) {
        return agentExecutionService.cancelResearchTask(userId, threadId, taskId);
    }

    @GetMapping("/{taskId}/report")
    public ResearchReportView getReport(@CurrentUserId String userId,
                                        @PathVariable String threadId,
                                        @PathVariable String taskId) {
        return taskResearchReadService.getReport(userId, threadId, taskId);
    }

    @GetMapping("/{taskId}/plan")
    public List<ResearchReportSection> getPlan(@CurrentUserId String userId,
                                               @PathVariable String threadId,
                                               @PathVariable String taskId) {
        return taskResearchReadService.getPlan(userId, threadId, taskId);
    }

    @GetMapping("/{taskId}/iterations")
    public List<ResearchIterationRecord> getIterations(@CurrentUserId String userId,
                                                       @PathVariable String threadId,
                                                       @PathVariable String taskId) {
        return taskResearchReadService.getIterations(userId, threadId, taskId);
    }

    @GetMapping("/{taskId}/findings")
    public List<ResearchFindingRecord> getFindings(@CurrentUserId String userId,
                                                   @PathVariable String threadId,
                                                   @PathVariable String taskId) {
        return taskResearchReadService.getFindings(userId, threadId, taskId);
    }

    @GetMapping("/{taskId}/sources")
    public List<ResearchSourceRecord> getSources(@CurrentUserId String userId,
                                                 @PathVariable String threadId,
                                                 @PathVariable String taskId) {
        return taskResearchReadService.getSources(userId, threadId, taskId);
    }

    @GetMapping("/{taskId}/citations")
    public List<ReportCitation> getCitations(@CurrentUserId String userId,
                                             @PathVariable String threadId,
                                             @PathVariable String taskId) {
        return taskResearchReadService.getCitations(userId, threadId, taskId);
    }
}
