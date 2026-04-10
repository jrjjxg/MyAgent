package com.xg.platform.shared.port;

import com.xg.platform.contracts.shared.task.TaskKind;
import com.xg.platform.contracts.shared.task.TaskRecord;
import com.xg.platform.contracts.shared.task.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    TaskRecord createQueuedTask(String userId,
                                String workspaceId,
                                String threadId,
                                String documentId,
                                String taskId,
                                String agentId,
                                TaskKind kind,
                                String title,
                                String summary,
                                String linkedDraftId,
                                int maxAttempts);

    default TaskRecord createQueuedTask(String userId,
                                        String threadId,
                                        String taskId,
                                        String agentId,
                                        TaskKind kind,
                                        String title,
                                        String summary,
                                        String linkedDraftId) {
        return createQueuedTask(userId, null, threadId, null, taskId, agentId, kind, title, summary, linkedDraftId, 3);
    }

    TaskRecord updateTask(String userId,
                          String threadId,
                          String taskId,
                          TaskStatus status,
                          String title,
                          String summary,
                          String stage,
                          Integer progress,
                          String linkedDraftId,
                          String resultArtifactId);

    List<TaskRecord> listTasks(String userId, String threadId);

    Optional<TaskRecord> findTask(String userId, String threadId, String taskId);

    default Optional<TaskRecord> findTaskById(String userId, String taskId) {
        return Optional.empty();
    }

    default List<TaskRecord> listWorkspaceTasks(String userId, String workspaceId, TaskKind kind) {
        return List.of();
    }

    default Optional<TaskRecord> findWorkspaceTask(String userId, String workspaceId, String taskId) {
        return Optional.empty();
    }

    default Optional<TaskRecord> findIngestTaskByDocument(String userId, String workspaceId, String documentId) {
        return Optional.empty();
    }

    default Optional<TaskRecord> createQueuedIngestTaskIfAbsent(String userId,
                                                                String workspaceId,
                                                                String threadId,
                                                                String documentId,
                                                                String taskId,
                                                                String title,
                                                                String summary,
                                                                int maxAttempts) {
        throw new UnsupportedOperationException("Ingest task creation is not supported");
    }

    default Optional<TaskRecord> claimQueuedOrStaleRunningTask(String userId,
                                                               String taskId,
                                                               Instant staleRunningBefore,
                                                               String summary,
                                                               String stage,
                                                               Integer progress) {
        throw new UnsupportedOperationException("Task claiming is not supported");
    }

    default TaskRecord requeueTask(String userId,
                                   String taskId,
                                   String summary,
                                   String stage,
                                   Integer progress,
                                   String lastError) {
        throw new UnsupportedOperationException("Task requeue is not supported");
    }

    default TaskRecord resetTaskToQueued(String userId,
                                         String taskId,
                                         String summary,
                                         String stage,
                                         Integer progress) {
        throw new UnsupportedOperationException("Task reset is not supported");
    }

    default TaskRecord markCompleted(String userId,
                                     String taskId,
                                     String summary,
                                     String resultArtifactId) {
        throw new UnsupportedOperationException("Task completion by id is not supported");
    }

    default TaskRecord markFailed(String userId,
                                  String taskId,
                                  String summary,
                                  String stage,
                                  Integer progress,
                                  String lastError,
                                  boolean terminal) {
        throw new UnsupportedOperationException("Task failure by id is not supported");
    }

    void deleteByThread(String userId, String threadId);

    default TaskRecord markRunning(String userId,
                                   String threadId,
                                   String taskId,
                                   String summary,
                                   String stage,
                                   Integer progress) {
        return updateTask(userId, threadId, taskId, TaskStatus.RUNNING, null, summary, stage, progress, null, null);
    }

    default TaskRecord markCompleted(String userId,
                                     String threadId,
                                     String taskId,
                                     String summary,
                                     String resultArtifactId) {
        return updateTask(userId, threadId, taskId, TaskStatus.COMPLETED, null, summary, "completed", 100, null, resultArtifactId);
    }

    default TaskRecord markFailed(String userId, String threadId, String taskId, String summary) {
        return updateTask(userId, threadId, taskId, TaskStatus.FAILED, null, summary, "failed", null, null, null);
    }

    default TaskRecord markCancelled(String userId, String threadId, String taskId, String summary) {
        return updateTask(userId, threadId, taskId, TaskStatus.CANCELLED, null, summary, "cancelled", null, null, null);
    }
}
