package com.xg.platform.api.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.runtime.WorkspaceRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DocumentController {

    private final ThreadRuntimeService threadRuntimeService;
    private final WorkspaceRuntimeService workspaceRuntimeService;
    private final DocumentStore documentStore;

    public DocumentController(ThreadRuntimeService threadRuntimeService,
                              WorkspaceRuntimeService workspaceRuntimeService,
                              DocumentStore documentStore) {
        this.threadRuntimeService = threadRuntimeService;
        this.workspaceRuntimeService = workspaceRuntimeService;
        this.documentStore = documentStore;
    }

    @GetMapping("/threads/{threadId}/documents")
    public List<DocumentRecord> listDocuments(@CurrentUserId String userId,
                                              @PathVariable String threadId) {
        var thread = threadRuntimeService.getThread(userId, threadId);
        return documentStore.listDocumentsByWorkspace(userId, thread.workspaceId());
    }

    @GetMapping("/workspaces/{workspaceId}/documents")
    public List<DocumentRecord> listWorkspaceDocuments(@CurrentUserId String userId,
                                                       @PathVariable String workspaceId) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        return documentStore.listDocumentsByWorkspace(userId, workspaceId);
    }
}
