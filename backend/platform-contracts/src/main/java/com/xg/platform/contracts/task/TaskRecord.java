package com.xg.platform.contracts.task;

import java.time.Instant;

public record TaskRecord(
        String taskId,
        String workspaceId,
        String threadId,
        String documentId,
        String agentId,
        TaskKind kind,
        TaskStatus status,
        String title,
        String summary,
        String stage,
        Integer progress,
        String linkedDraftId,
        String resultArtifactId,
        int attemptCount,
        int maxAttempts,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
) {

    public TaskRecord(String taskId,
                      String threadId,
                      String agentId,
                      TaskKind kind,
                      TaskStatus status,
                      String title,
                      String summary,
                      String stage,
                      Integer progress,
                      String linkedDraftId,
                      String resultArtifactId,
                      Instant createdAt,
                      Instant updatedAt) {
        this(
                taskId,
                null,
                threadId,
                null,
                agentId,
                kind,
                status,
                title,
                summary,
                stage,
                progress,
                linkedDraftId,
                resultArtifactId,
                0,
                3,
                null,
                createdAt,
                updatedAt,
                null,
                null
        );
    }
}
