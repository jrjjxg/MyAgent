package com.xg.platform.api.web.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.contracts.shared.task.TaskRecord;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.workspace.application.WorkspaceService;
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

    private final WorkspaceService workspaceRuntimeService;
    private final TaskRepository taskRepository;

    public WorkspaceTaskController(WorkspaceService workspaceRuntimeService,
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
