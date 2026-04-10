package com.xg.platform.workspace.application;

import com.xg.platform.contracts.workspace.WorkspaceRecord;

import java.util.List;
import com.xg.platform.workspace.port.WorkspaceRepository;

public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public WorkspaceRecord createWorkspace(String userId, String title) {
        return workspaceRepository.createWorkspace(userId, title);
    }

    public List<WorkspaceRecord> listWorkspaces(String userId) {
        return workspaceRepository.listWorkspaces(userId);
    }

    public WorkspaceRecord getWorkspace(String userId, String workspaceId) {
        return workspaceRepository.getWorkspace(userId, workspaceId);
    }

    public WorkspaceRecord touchWorkspace(String userId, String workspaceId) {
        return workspaceRepository.touchWorkspace(userId, workspaceId);
    }
}
