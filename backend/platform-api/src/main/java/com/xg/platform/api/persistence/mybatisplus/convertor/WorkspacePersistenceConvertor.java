package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.xg.platform.api.persistence.mybatisplus.entity.WorkspaceEntity;
import com.xg.platform.contracts.workspace.WorkspaceRecord;
import com.xg.platform.contracts.workspace.WorkspaceStatus;

public final class WorkspacePersistenceConvertor {

    private WorkspacePersistenceConvertor() {
    }

    public static WorkspaceRecord toRecord(WorkspaceEntity entity) {
        return new WorkspaceRecord(
                entity.getWorkspaceId(),
                entity.getUserId(),
                entity.getTitle(),
                WorkspaceStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static WorkspaceEntity toEntity(WorkspaceRecord record) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setWorkspaceId(record.workspaceId());
        entity.setUserId(record.userId());
        entity.setTitle(record.title());
        entity.setStatus(record.status().name());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }
}
