package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.xg.platform.api.persistence.mybatisplus.entity.TaskEntity;
import com.xg.platform.contracts.task.TaskKind;
import com.xg.platform.contracts.task.TaskRecord;
import com.xg.platform.contracts.task.TaskStatus;

public final class TaskPersistenceConvertor {

    private TaskPersistenceConvertor() {
    }

    public static TaskRecord toRecord(TaskEntity entity) {
        return new TaskRecord(
                entity.getTaskId(),
                entity.getWorkspaceId(),
                entity.getThreadId(),
                entity.getDocumentId(),
                entity.getAgentId(),
                TaskKind.valueOf(entity.getKind()),
                TaskStatus.valueOf(entity.getStatus()),
                entity.getTitle(),
                entity.getSummary(),
                entity.getStage(),
                entity.getProgress(),
                entity.getLinkedDraftId(),
                entity.getResultArtifactId(),
                entity.getAttemptCount() == null ? 0 : entity.getAttemptCount(),
                entity.getMaxAttempts() == null ? 3 : entity.getMaxAttempts(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    public static TaskEntity toEntity(String userId, TaskRecord record) {
        TaskEntity entity = new TaskEntity();
        entity.setTaskId(record.taskId());
        entity.setUserId(userId);
        entity.setWorkspaceId(record.workspaceId());
        entity.setThreadId(record.threadId());
        entity.setDocumentId(record.documentId());
        entity.setAgentId(record.agentId());
        entity.setKind(record.kind().name());
        entity.setStatus(record.status().name());
        entity.setTitle(record.title());
        entity.setSummary(record.summary());
        entity.setStage(record.stage());
        entity.setProgress(record.progress());
        entity.setLinkedDraftId(record.linkedDraftId());
        entity.setResultArtifactId(record.resultArtifactId());
        entity.setAttemptCount(record.attemptCount());
        entity.setMaxAttempts(record.maxAttempts());
        entity.setLastError(record.lastError());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        entity.setStartedAt(record.startedAt());
        entity.setCompletedAt(record.completedAt());
        return entity;
    }
}
