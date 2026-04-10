package com.xg.platform.api.web.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.workspace.CreateThreadRequest;
import com.xg.platform.contracts.workspace.ThreadRecord;
import com.xg.platform.contracts.workspace.CreateWorkspaceRequest;
import com.xg.platform.contracts.workspace.WorkspaceRecord;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.WorkspaceService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceRuntimeService;
    private final ThreadService threadRuntimeService;
    private final WorkspaceManager workspaceManager;

    public WorkspaceController(WorkspaceService workspaceRuntimeService,
                               ThreadService threadRuntimeService,
                               WorkspaceManager workspaceManager) {
        this.workspaceRuntimeService = workspaceRuntimeService;
        this.threadRuntimeService = threadRuntimeService;
        this.workspaceManager = workspaceManager;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceRecord createWorkspace(@CurrentUserId String userId,
                                           @RequestBody(required = false) CreateWorkspaceRequest request) {
        WorkspaceRecord workspace = workspaceRuntimeService.createWorkspace(userId, request == null ? null : request.title());
        workspaceManager.ensureWorkspace(userId, workspace.workspaceId());
        return workspace;
    }

    @GetMapping
    public List<WorkspaceRecord> listWorkspaces(@CurrentUserId String userId) {
        return workspaceRuntimeService.listWorkspaces(userId);
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceRecord getWorkspace(@CurrentUserId String userId,
                                        @PathVariable String workspaceId) {
        return workspaceRuntimeService.getWorkspace(userId, workspaceId);
    }

    @PostMapping("/{workspaceId}/threads")
    @ResponseStatus(HttpStatus.CREATED)
    public ThreadRecord createThread(@CurrentUserId String userId,
                                     @PathVariable String workspaceId,
                                     @RequestBody(required = false) CreateThreadRequest request) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        ThreadRecord thread = threadRuntimeService.createThread(userId, workspaceId, request == null ? null : request.title());
        workspaceManager.ensureWorkspace(userId, workspaceId);
        workspaceManager.ensureThreadWorkspace(userId, thread.threadId());
        return thread;
    }

    @GetMapping("/{workspaceId}/threads")
    public List<ThreadRecord> listThreads(@CurrentUserId String userId,
                                          @PathVariable String workspaceId) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        return threadRuntimeService.listThreads(userId, workspaceId);
    }
}
