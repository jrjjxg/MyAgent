package com.xg.platform.runtime;

import com.xg.platform.contracts.workspace.WorkspaceRecord;

import java.util.List;

public interface WorkspaceRepository {

    WorkspaceRecord createWorkspace(String userId, String title);

    List<WorkspaceRecord> listWorkspaces(String userId);

    WorkspaceRecord getWorkspace(String userId, String workspaceId);

    WorkspaceRecord touchWorkspace(String userId, String workspaceId);
}
