package com.xg.platform.api.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.task.TaskKind;
import com.xg.platform.contracts.task.TaskRecord;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.WorkspaceRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/workspaces/{workspaceId}/tasks")
public class WorkspaceTaskController {

    private final WorkspaceRuntimeService workspaceRuntimeService;
    private final TaskRepository taskRepository;

    public WorkspaceTaskController(WorkspaceRuntimeService workspaceRuntimeService,
                                   TaskRepository taskRepository) {
        this.workspaceRuntimeService = workspaceRuntimeService;
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public List<TaskRecord> listTasks(@CurrentUserId String userId,
                                      @PathVariable String workspaceId,
                                      @RequestParam(required = false) TaskKind kind) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        return taskRepository.listWorkspaceTasks(userId, workspaceId, kind);
    }

    @GetMapping("/{taskId}")
    public TaskRecord getTask(@CurrentUserId String userId,
                              @PathVariable String workspaceId,
                              @PathVariable String taskId) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        return taskRepository.findWorkspaceTask(userId, workspaceId, taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
    }
}
