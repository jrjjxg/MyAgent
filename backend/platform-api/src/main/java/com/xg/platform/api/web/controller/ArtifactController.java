package com.xg.platform.api.web.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.WorkspaceService;
import com.xg.platform.workspace.application.ArtifactService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ArtifactController {

    private final ThreadService threadRuntimeService;
    private final WorkspaceService workspaceRuntimeService;
    private final ArtifactService artifactService;

    public ArtifactController(ThreadService threadRuntimeService,
                              WorkspaceService workspaceRuntimeService,
                              ArtifactService artifactService) {
        this.threadRuntimeService = threadRuntimeService;
        this.workspaceRuntimeService = workspaceRuntimeService;
        this.artifactService = artifactService;
    }

    @GetMapping("/threads/{threadId}/artifacts")
    public List<ArtifactRecord> listArtifacts(@CurrentUserId String userId,
                                              @PathVariable String threadId,
                                              @RequestParam(name = "includeInternal", defaultValue = "false") boolean includeInternal) {
        var thread = threadRuntimeService.getThread(userId, threadId);
        return artifactService.listArtifactsByWorkspace(userId, thread.workspaceId(), includeInternal);
    }

    @GetMapping("/workspaces/{workspaceId}/artifacts")
    public List<ArtifactRecord> listWorkspaceArtifacts(@CurrentUserId String userId,
                                                       @PathVariable String workspaceId,
                                                       @RequestParam(name = "includeInternal", defaultValue = "false") boolean includeInternal) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        return artifactService.listArtifactsByWorkspace(userId, workspaceId, includeInternal);
    }
}
