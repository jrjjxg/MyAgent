package com.xg.platform.api.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.runtime.WorkspaceRuntimeService;
import com.xg.platform.workspace.ArtifactService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ArtifactController {

    private final ThreadRuntimeService threadRuntimeService;
    private final WorkspaceRuntimeService workspaceRuntimeService;
    private final ArtifactService artifactService;

    public ArtifactController(ThreadRuntimeService threadRuntimeService,
                              WorkspaceRuntimeService workspaceRuntimeService,
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
