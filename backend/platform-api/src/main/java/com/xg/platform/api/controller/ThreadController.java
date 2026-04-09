package com.xg.platform.api.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.thread.CreateThreadRequest;
import com.xg.platform.contracts.thread.ThreadRecord;
import com.xg.platform.runtime.ThreadDeletionService;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.runtime.WorkspaceRuntimeService;
import com.xg.platform.workspace.WorkspaceManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/threads")
public class ThreadController {

    private final ThreadRuntimeService threadRuntimeService;
    private final WorkspaceRuntimeService workspaceRuntimeService;
    private final WorkspaceManager workspaceManager;
    private final ThreadDeletionService threadDeletionService;

    public ThreadController(ThreadRuntimeService threadRuntimeService,
                            WorkspaceRuntimeService workspaceRuntimeService,
                            WorkspaceManager workspaceManager,
                            ThreadDeletionService threadDeletionService) {
        this.threadRuntimeService = threadRuntimeService;
        this.workspaceRuntimeService = workspaceRuntimeService;
        this.workspaceManager = workspaceManager;
        this.threadDeletionService = threadDeletionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ThreadRecord createThread(@CurrentUserId String userId,
                                     @RequestBody(required = false) CreateThreadRequest request) {
        if (request == null || request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        workspaceRuntimeService.getWorkspace(userId, request.workspaceId());
        ThreadRecord thread = threadRuntimeService.createThread(userId, request.workspaceId(), request.title());
        workspaceManager.ensureWorkspace(userId, thread.workspaceId());
        workspaceManager.ensureThreadWorkspace(userId, thread.threadId());
        return thread;
    }

    @GetMapping
    public List<ThreadRecord> listThreads(@CurrentUserId String userId) {
        return threadRuntimeService.listThreads(userId);
    }

    @DeleteMapping("/{threadId}")
    public ResponseEntity<Void> deleteThread(@CurrentUserId String userId,
                                             @PathVariable String threadId) {
        threadDeletionService.deleteThread(userId, threadId);
        return ResponseEntity.noContent().build();
    }
}
