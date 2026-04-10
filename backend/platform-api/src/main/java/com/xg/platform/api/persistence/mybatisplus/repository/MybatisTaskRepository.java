package com.xg.platform.api.persistence.mybatisplus.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.persistence.mybatisplus.convertor.TaskPersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.TaskEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.TaskMapper;
import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.contracts.shared.task.TaskRecord;
import com.xg.platform.contracts.shared.task.TaskStatus;
import com.xg.platform.shared.port.TaskRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class MybatisTaskRepository implements TaskRepository {

    private final TaskMapper taskMapper;

    public MybatisTaskRepository(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public TaskRecord createQueuedTask(String userId,
                                       String workspaceId,
                                       String threadId,
                                       String documentId,
                                       String taskId,
                                       String agentId,
                                       TaskKind kind,
                                       String title,
                                       String summary,
                                       String linkedDraftId,
                                       int maxAttempts) {
        Instant now = Instant.now();
        TaskRecord task = new TaskRecord(
                taskId,
                workspaceId,
                threadId,
                documentId,
                agentId,
                kind,
                TaskStatus.QUEUED,
                title,
                summary,
                "queued",
                0,
                linkedDraftId,
                null,
                0,
                Math.max(1, maxAttempts),
                null,
                now,
                now,
                null,
                null
        );
        taskMapper.insert(TaskPersistenceConvertor.toEntity(userId, task));
        return task;
    }

    @Override
    public TaskRecord updateTask(String userId,
                                 String threadId,
                                 String taskId,
                                 TaskStatus status,
                                 String title,
                                 String summary,
                                 String stage,
                                 Integer progress,
                                 String linkedDraftId,
                                 String resultArtifactId) {
        TaskRecord existing = findTask(userId, threadId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        TaskRecord updated = new TaskRecord(
                existing.taskId(),
                existing.workspaceId(),
                existing.threadId(),
                existing.documentId(),
                existing.agentId(),
                existing.kind(),
                status == null ? existing.status() : status,
                title == null ? existing.title() : title,
                summary == null ? existing.summary() : summary,
                stage == null ? existing.stage() : stage,
                progress == null ? existing.progress() : progress,
                linkedDraftId == null ? existing.linkedDraftId() : linkedDraftId,
                resultArtifactId == null ? existing.resultArtifactId() : resultArtifactId,
                existing.attemptCount(),
                existing.maxAttempts(),
                existing.lastError(),
                existing.createdAt(),
                Instant.now(),
                existing.startedAt(),
                existing.completedAt()
        );
        updateById(userId, updated);
        return updated;
    }

    @Override
    public List<TaskRecord> listTasks(String userId, String threadId) {
        return taskMapper.selectList(
                        Wrappers.<TaskEntity>lambdaQuery()
                                .eq(TaskEntity::getUserId, userId)
                                .eq(TaskEntity::getThreadId, threadId)
                                .orderByDesc(TaskEntity::getUpdatedAt))
                .stream()
                .map(TaskPersistenceConvertor::toRecord)
                .toList();
    }

    @Override
    public Optional<TaskRecord> findTask(String userId, String threadId, String taskId) {
        TaskEntity entity = taskMapper.selectOne(
                Wrappers.<TaskEntity>lambdaQuery()
                        .eq(TaskEntity::getUserId, userId)
                        .eq(TaskEntity::getThreadId, threadId)
                        .eq(TaskEntity::getTaskId, taskId)
        );
        return Optional.ofNullable(entity).map(TaskPersistenceConvertor::toRecord);
    }

    @Override
    public Optional<TaskRecord> findTaskById(String userId, String taskId) {
        return Optional.ofNullable(taskMapper.selectByUserAndTaskId(userId, taskId))
                .map(TaskPersistenceConvertor::toRecord);
    }

    @Override
    public List<TaskRecord> listWorkspaceTasks(String userId, String workspaceId, TaskKind kind) {
        return taskMapper.listByWorkspace(userId, workspaceId, kind == null ? null : kind.name()).stream()
                .map(TaskPersistenceConvertor::toRecord)
                .toList();
    }

    @Override
    public Optional<TaskRecord> findWorkspaceTask(String userId, String workspaceId, String taskId) {
        TaskEntity entity = taskMapper.selectOne(
                Wrappers.<TaskEntity>lambdaQuery()
                        .eq(TaskEntity::getUserId, userId)
                        .eq(TaskEntity::getWorkspaceId, workspaceId)
                        .eq(TaskEntity::getTaskId, taskId)
        );
        return Optional.ofNullable(entity).map(TaskPersistenceConvertor::toRecord);
    }

    @Override
    public Optional<TaskRecord> findIngestTaskByDocument(String userId, String workspaceId, String documentId) {
        return Optional.ofNullable(taskMapper.findIngestTaskByDocument(userId, workspaceId, documentId))
                .map(TaskPersistenceConvertor::toRecord);
    }

    @Override
    public Optional<TaskRecord> createQueuedIngestTaskIfAbsent(String userId,
                                                               String workspaceId,
                                                               String threadId,
                                                               String documentId,
                                                               String taskId,
                                                               String title,
                                                               String summary,
                                                               int maxAttempts) {
        Instant now = Instant.now();
        TaskRecord task = new TaskRecord(
                taskId,
                workspaceId,
                threadId,
                documentId,
                "ingest-service",
                TaskKind.INGEST,
                TaskStatus.QUEUED,
                title,
                summary,
                "queued",
                0,
                null,
                null,
                0,
                Math.max(1, maxAttempts),
                null,
                now,
                now,
                null,
                null
        );
        TaskEntity inserted = taskMapper.insertQueuedIngestIfAbsent(TaskPersistenceConvertor.toEntity(userId, task));
        return Optional.ofNullable(inserted).map(TaskPersistenceConvertor::toRecord);
    }

    @Override
    public Optional<TaskRecord> claimQueuedOrStaleRunningTask(String userId,
                                                              String taskId,
                                                              Instant staleRunningBefore,
                                                              String summary,
                                                              String stage,
                                                              Integer progress) {
        Instant now = Instant.now();
        int updated = taskMapper.claimQueuedOrStaleRunningTask(
                userId,
                taskId,
                summary,
                stage,
                progress,
                now,
                staleRunningBefore
        );
        if (updated == 0) {
            return Optional.empty();
        }
        return findTaskById(userId, taskId);
    }

    @Override
    public TaskRecord requeueTask(String userId,
                                  String taskId,
                                  String summary,
                                  String stage,
                                  Integer progress,
                                  String lastError) {
        TaskRecord existing = requireTask(userId, taskId);
        TaskRecord updated = new TaskRecord(
                existing.taskId(),
                existing.workspaceId(),
                existing.threadId(),
                existing.documentId(),
                existing.agentId(),
                existing.kind(),
                TaskStatus.QUEUED,
                existing.title(),
                summary == null ? existing.summary() : summary,
                stage == null ? existing.stage() : stage,
                progress == null ? existing.progress() : progress,
                existing.linkedDraftId(),
                existing.resultArtifactId(),
                existing.attemptCount() + 1,
                existing.maxAttempts(),
                lastError,
                existing.createdAt(),
                Instant.now(),
                null,
                null
        );
        updateById(userId, updated);
        return updated;
    }

    @Override
    public TaskRecord resetTaskToQueued(String userId,
                                        String taskId,
                                        String summary,
                                        String stage,
                                        Integer progress) {
        TaskRecord existing = requireTask(userId, taskId);
        TaskRecord updated = new TaskRecord(
                existing.taskId(),
                existing.workspaceId(),
                existing.threadId(),
                existing.documentId(),
                existing.agentId(),
                existing.kind(),
                TaskStatus.QUEUED,
                existing.title(),
                summary == null ? existing.summary() : summary,
                stage == null ? existing.stage() : stage,
                progress == null ? existing.progress() : progress,
                existing.linkedDraftId(),
                existing.resultArtifactId(),
                existing.attemptCount(),
                existing.maxAttempts(),
                null,
                existing.createdAt(),
                Instant.now(),
                null,
                null
        );
        updateById(userId, updated);
        return updated;
    }

    @Override
    public TaskRecord markCompleted(String userId,
                                    String taskId,
                                    String summary,
                                    String resultArtifactId) {
        TaskRecord existing = requireTask(userId, taskId);
        Instant now = Instant.now();
        TaskRecord updated = new TaskRecord(
                existing.taskId(),
                existing.workspaceId(),
                existing.threadId(),
                existing.documentId(),
                existing.agentId(),
                existing.kind(),
                TaskStatus.COMPLETED,
                existing.title(),
                summary == null ? existing.summary() : summary,
                "completed",
                100,
                existing.linkedDraftId(),
                resultArtifactId == null ? existing.resultArtifactId() : resultArtifactId,
                existing.attemptCount(),
                existing.maxAttempts(),
                null,
                existing.createdAt(),
                now,
                existing.startedAt(),
                now
        );
        updateById(userId, updated);
        return updated;
    }

    @Override
    public TaskRecord markFailed(String userId,
                                 String taskId,
                                 String summary,
                                 String stage,
                                 Integer progress,
                                 String lastError,
                                 boolean terminal) {
        TaskRecord existing = requireTask(userId, taskId);
        Instant now = Instant.now();
        TaskRecord updated = new TaskRecord(
                existing.taskId(),
                existing.workspaceId(),
                existing.threadId(),
                existing.documentId(),
                existing.agentId(),
                existing.kind(),
                terminal ? TaskStatus.FAILED : TaskStatus.QUEUED,
                existing.title(),
                summary == null ? existing.summary() : summary,
                stage == null ? existing.stage() : stage,
                progress == null ? existing.progress() : progress,
                existing.linkedDraftId(),
                existing.resultArtifactId(),
                existing.attemptCount() + 1,
                existing.maxAttempts(),
                lastError,
                existing.createdAt(),
                now,
                null,
                terminal ? now : null
        );
        updateById(userId, updated);
        return updated;
    }

    @Override
    public void deleteByThread(String userId, String threadId) {
        taskMapper.delete(
                Wrappers.<TaskEntity>lambdaQuery()
                        .eq(TaskEntity::getUserId, userId)
                        .eq(TaskEntity::getThreadId, threadId)
        );
    }

    private TaskRecord requireTask(String userId, String taskId) {
        return findTaskById(userId, taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId));
    }

    private void updateById(String userId, TaskRecord updated) {
        taskMapper.update(
                null,
                Wrappers.<TaskEntity>lambdaUpdate()
                        .set(TaskEntity::getWorkspaceId, updated.workspaceId())
                        .set(TaskEntity::getThreadId, updated.threadId())
                        .set(TaskEntity::getDocumentId, updated.documentId())
                        .set(TaskEntity::getAgentId, updated.agentId())
                        .set(TaskEntity::getKind, updated.kind().name())
                        .set(TaskEntity::getStatus, updated.status().name())
                        .set(TaskEntity::getTitle, updated.title())
                        .set(TaskEntity::getSummary, updated.summary())
                        .set(TaskEntity::getStage, updated.stage())
                        .set(TaskEntity::getProgress, updated.progress())
                        .set(TaskEntity::getLinkedDraftId, updated.linkedDraftId())
                        .set(TaskEntity::getResultArtifactId, updated.resultArtifactId())
                        .set(TaskEntity::getAttemptCount, updated.attemptCount())
                        .set(TaskEntity::getMaxAttempts, updated.maxAttempts())
                        .set(TaskEntity::getLastError, updated.lastError())
                        .set(TaskEntity::getUpdatedAt, updated.updatedAt())
                        .set(TaskEntity::getStartedAt, updated.startedAt())
                        .set(TaskEntity::getCompletedAt, updated.completedAt())
                        .eq(TaskEntity::getUserId, userId)
                        .eq(TaskEntity::getTaskId, updated.taskId())
        );
    }
}
