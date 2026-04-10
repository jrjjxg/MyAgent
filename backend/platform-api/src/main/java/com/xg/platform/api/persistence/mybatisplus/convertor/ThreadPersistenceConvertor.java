package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.xg.platform.api.persistence.mybatisplus.entity.ThreadEntity;
import com.xg.platform.contracts.workspace.ThreadRecord;
import com.xg.platform.contracts.workspace.ThreadStatus;

public final class ThreadPersistenceConvertor {

    private ThreadPersistenceConvertor() {
    }

    public static ThreadRecord toRecord(ThreadEntity entity) {
        return new ThreadRecord(
                entity.getThreadId(),
                entity.getUserId(),
                entity.getWorkspaceId(),
                entity.getTitle(),
                ThreadStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static ThreadEntity toEntity(ThreadRecord record) {
        ThreadEntity entity = new ThreadEntity();
        entity.setThreadId(record.threadId());
        entity.setUserId(record.userId());
        entity.setWorkspaceId(record.workspaceId());
        entity.setTitle(record.title());
        entity.setStatus(record.status().name());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }
}
