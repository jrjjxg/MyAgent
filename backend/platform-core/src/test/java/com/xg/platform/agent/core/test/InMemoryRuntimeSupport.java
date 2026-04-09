package com.xg.platform.agent.core.test;

import com.xg.platform.contracts.memory.CreateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryStatus;
import com.xg.platform.contracts.memory.LongTermMemoryType;
import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;
import com.xg.platform.contracts.memory.MemoryExtractionJobStatus;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.contracts.memory.UpdateLongTermMemoryRequest;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.ResearchDraftRecord;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.research.ResearchTaskSnapshotRecord;
import com.xg.platform.contracts.task.TaskKind;
import com.xg.platform.contracts.task.TaskRecord;
import com.xg.platform.contracts.task.TaskStatus;
import com.xg.platform.contracts.thread.ThreadRecord;
import com.xg.platform.contracts.thread.ThreadStatus;
import com.xg.platform.runtime.LongTermMemoryRepository;
import com.xg.platform.runtime.LongTermMemoryJobRepository;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.ResearchDraftRepository;
import com.xg.platform.runtime.ResearchTaskSnapshotRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.ThreadMemorySnapshotRepository;
import com.xg.platform.runtime.ThreadRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryRuntimeSupport {

    private InMemoryRuntimeSupport() {
    }

    public static final class InMemoryThreadRepository implements ThreadRepository {

        private final Map<String, List<ThreadRecord>> threadsByUser = new LinkedHashMap<>();

        @Override
        public synchronized ThreadRecord createThread(String userId, String workspaceId, String title) {
            Instant now = Instant.now();
            ThreadRecord record = new ThreadRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    workspaceId,
                    title == null || title.isBlank() ? "New thread" : title.trim(),
                    ThreadStatus.IDLE,
                    now,
                    now
            );
            threadsByUser.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(record);
            return record;
        }

        @Override
        public synchronized List<ThreadRecord> listThreads(String userId) {
            return threadsByUser.getOrDefault(userId, List.of()).stream()
                    .sorted(Comparator.comparing(ThreadRecord::updatedAt).reversed())
                    .toList();
        }

        @Override
        public synchronized List<ThreadRecord> listThreads(String userId, String workspaceId) {
            return threadsByUser.getOrDefault(userId, List.of()).stream()
                    .filter(thread -> thread.workspaceId().equals(workspaceId))
                    .sorted(Comparator.comparing(ThreadRecord::updatedAt).reversed())
                    .toList();
        }

        @Override
        public synchronized ThreadRecord getThread(String userId, String threadId) {
            return threadsByUser.getOrDefault(userId, List.of()).stream()
                    .filter(thread -> thread.threadId().equals(threadId))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Thread not found: " + threadId));
        }

        @Override
        public synchronized ThreadRecord touchThread(String userId, String threadId) {
            ThreadRecord existing = getThread(userId, threadId);
            ThreadRecord updated = new ThreadRecord(
                    existing.threadId(),
                    existing.userId(),
                    existing.workspaceId(),
                    existing.title(),
                    existing.status(),
                    existing.createdAt(),
                    Instant.now()
            );
            List<ThreadRecord> threads = new ArrayList<>(threadsByUser.getOrDefault(userId, List.of()));
            for (int index = 0; index < threads.size(); index++) {
                if (threads.get(index).threadId().equals(threadId)) {
                    threads.set(index, updated);
                    threadsByUser.put(userId, threads);
                    return updated;
                }
            }
            throw new NoSuchElementException("Thread not found: " + threadId);
        }

        @Override
        public synchronized void deleteThread(String userId, String threadId) {
            List<ThreadRecord> threads = new ArrayList<>(threadsByUser.getOrDefault(userId, List.of()));
            threads.removeIf(thread -> thread.threadId().equals(threadId));
            threadsByUser.put(userId, threads);
        }
    }

    public static final class InMemoryMessageRepository implements MessageRepository {

        private final Map<ThreadKey, List<MessageRecord>> messagesByThread = new LinkedHashMap<>();

        @Override
        public synchronized MessageRecord append(String userId, MessageRecord messageRecord) {
            messagesByThread.computeIfAbsent(new ThreadKey(userId, messageRecord.threadId()), ignored -> new ArrayList<>())
                    .add(messageRecord);
            return messageRecord;
        }

        @Override
        public synchronized List<MessageRecord> listMessages(String userId, String threadId) {
            return List.copyOf(messagesByThread.getOrDefault(new ThreadKey(userId, threadId), List.of()));
        }

        @Override
        public synchronized Optional<String> findLatestMessageId(String userId, String threadId) {
            List<MessageRecord> messages = messagesByThread.getOrDefault(new ThreadKey(userId, threadId), List.of());
            if (messages.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(messages.get(messages.size() - 1).messageId());
        }

        @Override
        public synchronized Optional<MessageRecord> findById(String userId, String threadId, String messageId) {
            return messagesByThread.getOrDefault(new ThreadKey(userId, threadId), List.of()).stream()
                    .filter(message -> message.messageId().equals(messageId))
                    .findFirst();
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            messagesByThread.remove(new ThreadKey(userId, threadId));
        }
    }

    public static final class InMemoryResearchDraftRepository implements ResearchDraftRepository {

        private final Map<ThreadKey, ResearchDraftRecord> draftsByThread = new LinkedHashMap<>();

        @Override
        public synchronized Optional<ResearchDraftRecord> findActiveDraft(String userId, String threadId) {
            return Optional.ofNullable(draftsByThread.get(new ThreadKey(userId, threadId)));
        }

        @Override
        public synchronized ResearchDraftRecord save(String userId, ResearchDraftRecord draftRecord) {
            draftsByThread.put(new ThreadKey(userId, draftRecord.threadId()), draftRecord);
            return draftRecord;
        }

        @Override
        public synchronized void clear(String userId, String threadId) {
            draftsByThread.remove(new ThreadKey(userId, threadId));
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            draftsByThread.remove(new ThreadKey(userId, threadId));
        }
    }

    public static final class InMemoryRunEventRepository implements RunEventRepository {

        private final Map<ThreadKey, List<RunEvent>> eventsByThread = new LinkedHashMap<>();

        @Override
        public synchronized void appendEvent(String userId, String threadId, RunEvent runEvent) {
            eventsByThread.computeIfAbsent(new ThreadKey(userId, threadId), ignored -> new ArrayList<>()).add(runEvent);
        }

        @Override
        public synchronized List<RunEvent> listEvents(String userId, String threadId, String runId) {
            return eventsByThread.getOrDefault(new ThreadKey(userId, threadId), List.of()).stream()
                    .filter(event -> event.runId().equals(runId))
                    .toList();
        }

        @Override
        public synchronized List<RunEvent> listEvents(String userId, String threadId, int limit) {
            return eventsByThread.getOrDefault(new ThreadKey(userId, threadId), List.of()).stream()
                    .sorted(Comparator.comparing(RunEvent::timestamp).reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        }

        @Override
        public synchronized List<RunEvent> listEvents(String userId, String threadId, List<TaskRecord> tasks, String taskId, int limit) {
            if (taskId != null && !taskId.isBlank()) {
                return listEvents(userId, threadId, taskId);
            }
            return listEvents(userId, threadId, limit);
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            eventsByThread.remove(new ThreadKey(userId, threadId));
        }
    }

    public static final class InMemoryTaskRepository implements TaskRepository {

        private final Map<UserTaskKey, TaskRecord> tasksById = new LinkedHashMap<>();

        @Override
        public synchronized TaskRecord createQueuedTask(String userId,
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
            tasksById.put(new UserTaskKey(userId, taskId), task);
            return task;
        }

        @Override
        public synchronized TaskRecord updateTask(String userId,
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
            tasksById.put(new UserTaskKey(userId, taskId), updated);
            return updated;
        }

        @Override
        public synchronized List<TaskRecord> listTasks(String userId, String threadId) {
            return tasksById.entrySet().stream()
                    .filter(entry -> entry.getKey().userId().equals(userId))
                    .map(Map.Entry::getValue)
                    .filter(task -> java.util.Objects.equals(task.threadId(), threadId))
                    .sorted(Comparator.comparing(TaskRecord::updatedAt).reversed())
                    .toList();
        }

        @Override
        public synchronized Optional<TaskRecord> findTask(String userId, String threadId, String taskId) {
            return tasksById.entrySet().stream()
                    .filter(entry -> entry.getKey().userId().equals(userId))
                    .map(Map.Entry::getValue)
                    .filter(task -> java.util.Objects.equals(task.threadId(), threadId))
                    .filter(task -> task.taskId().equals(taskId))
                    .findFirst();
        }

        @Override
        public synchronized Optional<TaskRecord> findTaskById(String userId, String taskId) {
            return Optional.ofNullable(tasksById.get(new UserTaskKey(userId, taskId)));
        }

        @Override
        public synchronized List<TaskRecord> listWorkspaceTasks(String userId, String workspaceId, TaskKind kind) {
            return tasksById.entrySet().stream()
                    .filter(entry -> entry.getKey().userId().equals(userId))
                    .map(Map.Entry::getValue)
                    .filter(task -> java.util.Objects.equals(task.workspaceId(), workspaceId))
                    .filter(task -> kind == null || task.kind() == kind)
                    .sorted(Comparator.comparing(TaskRecord::updatedAt).reversed())
                    .toList();
        }

        @Override
        public synchronized Optional<TaskRecord> findWorkspaceTask(String userId, String workspaceId, String taskId) {
            return findTaskById(userId, taskId)
                    .filter(task -> java.util.Objects.equals(task.workspaceId(), workspaceId));
        }

        @Override
        public synchronized Optional<TaskRecord> findIngestTaskByDocument(String userId, String workspaceId, String documentId) {
            return tasksById.entrySet().stream()
                    .filter(entry -> entry.getKey().userId().equals(userId))
                    .map(Map.Entry::getValue)
                    .filter(task -> task.kind() == TaskKind.INGEST)
                    .filter(task -> java.util.Objects.equals(task.workspaceId(), workspaceId))
                    .filter(task -> java.util.Objects.equals(task.documentId(), documentId))
                    .findFirst();
        }

        @Override
        public synchronized Optional<TaskRecord> createQueuedIngestTaskIfAbsent(String userId,
                                                                                String workspaceId,
                                                                                String threadId,
                                                                                String documentId,
                                                                                String taskId,
                                                                                String title,
                                                                                String summary,
                                                                                int maxAttempts) {
            if (findIngestTaskByDocument(userId, workspaceId, documentId).isPresent()) {
                return Optional.empty();
            }
            return Optional.of(createQueuedTask(
                    userId,
                    workspaceId,
                    threadId,
                    documentId,
                    taskId,
                    "ingest-service",
                    TaskKind.INGEST,
                    title,
                    summary,
                    null,
                    maxAttempts
            ));
        }

        @Override
        public synchronized Optional<TaskRecord> claimQueuedOrStaleRunningTask(String userId,
                                                                               String taskId,
                                                                               Instant staleRunningBefore,
                                                                               String summary,
                                                                               String stage,
                                                                               Integer progress) {
            TaskRecord existing = tasksById.get(new UserTaskKey(userId, taskId));
            if (existing == null) {
                return Optional.empty();
            }
            boolean claimable = existing.status() == TaskStatus.QUEUED
                    || (existing.status() == TaskStatus.RUNNING
                    && (existing.startedAt() == null || !existing.startedAt().isAfter(staleRunningBefore)));
            if (!claimable) {
                return Optional.empty();
            }
            Instant now = Instant.now();
            TaskRecord updated = new TaskRecord(
                    existing.taskId(),
                    existing.workspaceId(),
                    existing.threadId(),
                    existing.documentId(),
                    existing.agentId(),
                    existing.kind(),
                    TaskStatus.RUNNING,
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
                    now,
                    now,
                    null
            );
            tasksById.put(new UserTaskKey(userId, taskId), updated);
            return Optional.of(updated);
        }

        @Override
        public synchronized TaskRecord requeueTask(String userId,
                                                   String taskId,
                                                   String summary,
                                                   String stage,
                                                   Integer progress,
                                                   String lastError) {
            return updateRetryState(userId, taskId, summary, stage, progress, lastError, false, true);
        }

        @Override
        public synchronized TaskRecord resetTaskToQueued(String userId,
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
            tasksById.put(new UserTaskKey(userId, taskId), updated);
            return updated;
        }

        @Override
        public synchronized TaskRecord markCompleted(String userId,
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
            tasksById.put(new UserTaskKey(userId, taskId), updated);
            return updated;
        }

        @Override
        public synchronized TaskRecord markFailed(String userId,
                                                  String taskId,
                                                  String summary,
                                                  String stage,
                                                  Integer progress,
                                                  String lastError,
                                                  boolean terminal) {
            return updateRetryState(userId, taskId, summary, stage, progress, lastError, terminal, true);
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            tasksById.entrySet().removeIf(entry -> entry.getKey().userId().equals(userId)
                    && java.util.Objects.equals(entry.getValue().threadId(), threadId));
        }

        private TaskRecord updateRetryState(String userId,
                                            String taskId,
                                            String summary,
                                            String stage,
                                            Integer progress,
                                            String lastError,
                                            boolean terminal,
                                            boolean incrementAttempt) {
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
                    incrementAttempt ? existing.attemptCount() + 1 : existing.attemptCount(),
                    existing.maxAttempts(),
                    lastError,
                    existing.createdAt(),
                    now,
                    null,
                    terminal ? now : null
            );
            tasksById.put(new UserTaskKey(userId, taskId), updated);
            return updated;
        }

        private TaskRecord requireTask(String userId, String taskId) {
            return findTaskById(userId, taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        }
    }

    public static final class InMemoryThreadMemorySnapshotRepository implements ThreadMemorySnapshotRepository {

        private final Map<ThreadKey, ThreadMemorySnapshotRecord> snapshotsByThread = new LinkedHashMap<>();

        @Override
        public synchronized Optional<ThreadMemorySnapshotRecord> findByThread(String userId, String threadId) {
            return Optional.ofNullable(snapshotsByThread.get(new ThreadKey(userId, threadId)));
        }

        @Override
        public synchronized ThreadMemorySnapshotRecord save(String userId, ThreadMemorySnapshotRecord record) {
            snapshotsByThread.put(new ThreadKey(userId, record.threadId()), record);
            return record;
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            snapshotsByThread.remove(new ThreadKey(userId, threadId));
        }
    }

    public static final class InMemoryResearchTaskSnapshotRepository implements ResearchTaskSnapshotRepository {

        private final Map<UserTaskKey, ResearchTaskSnapshotRecord> snapshotsByTask = new LinkedHashMap<>();

        @Override
        public synchronized Optional<ResearchTaskSnapshotRecord> findByTask(String userId, String threadId, String taskId) {
            return Optional.ofNullable(snapshotsByTask.get(new UserTaskKey(userId, taskId)))
                    .filter(snapshot -> java.util.Objects.equals(snapshot.threadId(), threadId));
        }

        @Override
        public synchronized ResearchTaskSnapshotRecord save(String userId, ResearchTaskSnapshotRecord record) {
            snapshotsByTask.put(new UserTaskKey(userId, record.taskId()), record);
            return record;
        }

        @Override
        public synchronized void deleteByTask(String userId, String taskId) {
            snapshotsByTask.remove(new UserTaskKey(userId, taskId));
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            snapshotsByTask.entrySet().removeIf(entry -> entry.getKey().userId().equals(userId)
                    && java.util.Objects.equals(entry.getValue().threadId(), threadId));
        }
    }

    public static final class InMemoryLongTermMemoryRepository implements LongTermMemoryRepository {

        private final Map<String, List<LongTermMemoryRecord>> memoryByUser = new LinkedHashMap<>();

        @Override
        public synchronized List<LongTermMemoryRecord> listActive(String userId) {
            return memoryByUser.getOrDefault(userId, List.of()).stream()
                    .filter(memory -> memory.status() == LongTermMemoryStatus.ACTIVE)
                    .sorted(Comparator.comparing(LongTermMemoryRecord::updatedAt).reversed())
                    .toList();
        }

        @Override
        public synchronized Optional<LongTermMemoryRecord> findById(String userId, String memoryId) {
            return memoryByUser.getOrDefault(userId, List.of()).stream()
                    .filter(memory -> memory.memoryId().equals(memoryId))
                    .findFirst();
        }

        @Override
        public synchronized Optional<LongTermMemoryRecord> findActiveByTitle(String userId, String title) {
            return memoryByUser.getOrDefault(userId, List.of()).stream()
                    .filter(memory -> memory.status() == LongTermMemoryStatus.ACTIVE && memory.title().equals(title))
                    .findFirst();
        }

        @Override
        public synchronized Optional<LongTermMemoryRecord> findActiveByCanonicalKey(String userId,
                                                                                    LongTermMemoryType memoryType,
                                                                                    String canonicalKey) {
            return memoryByUser.getOrDefault(userId, List.of()).stream()
                    .filter(memory -> memory.status() == LongTermMemoryStatus.ACTIVE
                            && memory.memoryType() == memoryType
                            && java.util.Objects.equals(memory.canonicalKey(), canonicalKey))
                    .findFirst();
        }

        @Override
        public synchronized LongTermMemoryRecord create(String userId, CreateLongTermMemoryRequest request) {
            Instant now = Instant.now();
            LongTermMemoryRecord record = new LongTermMemoryRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    request.memoryType() == null ? LongTermMemoryType.SEMANTIC : request.memoryType(),
                    request.canonicalKey(),
                    request.title(),
                    request.content(),
                    request.sourceThreadId(),
                    request.sourceMessageId(),
                    request.sourceTaskId(),
                    LongTermMemoryStatus.ACTIVE,
                    now,
                    now
            );
            memoryByUser.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(record);
            return record;
        }

        @Override
        public synchronized LongTermMemoryRecord update(String userId, String memoryId, UpdateLongTermMemoryRequest request) {
            List<LongTermMemoryRecord> records = new ArrayList<>(memoryByUser.getOrDefault(userId, List.of()));
            for (int index = 0; index < records.size(); index++) {
                LongTermMemoryRecord existing = records.get(index);
                if (existing.memoryId().equals(memoryId)) {
                    LongTermMemoryRecord updated = new LongTermMemoryRecord(
                            existing.memoryId(),
                            existing.userId(),
                            request.memoryType() == null ? existing.memoryType() : request.memoryType(),
                            request.canonicalKey() == null ? existing.canonicalKey() : request.canonicalKey(),
                            request.title() == null ? existing.title() : request.title(),
                            request.content() == null ? existing.content() : request.content(),
                            request.sourceThreadId() == null ? existing.sourceThreadId() : request.sourceThreadId(),
                            request.sourceMessageId() == null ? existing.sourceMessageId() : request.sourceMessageId(),
                            request.sourceTaskId() == null ? existing.sourceTaskId() : request.sourceTaskId(),
                            existing.status(),
                            existing.createdAt(),
                            Instant.now()
                    );
                    records.set(index, updated);
                    memoryByUser.put(userId, records);
                    return updated;
                }
            }
            throw new NoSuchElementException("Long-term memory not found: " + memoryId);
        }

        @Override
        public synchronized void delete(String userId, String memoryId) {
            List<LongTermMemoryRecord> records = new ArrayList<>(memoryByUser.getOrDefault(userId, List.of()));
            for (int index = 0; index < records.size(); index++) {
                LongTermMemoryRecord existing = records.get(index);
                if (existing.memoryId().equals(memoryId)) {
                    records.set(index, new LongTermMemoryRecord(
                            existing.memoryId(),
                            existing.userId(),
                            existing.memoryType(),
                            existing.canonicalKey(),
                            existing.title(),
                            existing.content(),
                            existing.sourceThreadId(),
                            existing.sourceMessageId(),
                            existing.sourceTaskId(),
                            LongTermMemoryStatus.DELETED,
                            existing.createdAt(),
                            Instant.now()
                    ));
                    memoryByUser.put(userId, records);
                    return;
                }
            }
        }

        @Override
        public synchronized int deleteBySourceThread(String userId, String sourceThreadId) {
            int deleted = 0;
            List<LongTermMemoryRecord> records = new ArrayList<>(memoryByUser.getOrDefault(userId, List.of()));
            for (int index = 0; index < records.size(); index++) {
                LongTermMemoryRecord existing = records.get(index);
                if (existing.status() == LongTermMemoryStatus.ACTIVE
                        && java.util.Objects.equals(existing.sourceThreadId(), sourceThreadId)) {
                    records.set(index, new LongTermMemoryRecord(
                            existing.memoryId(),
                            existing.userId(),
                            existing.memoryType(),
                            existing.canonicalKey(),
                            existing.title(),
                            existing.content(),
                            existing.sourceThreadId(),
                            existing.sourceMessageId(),
                            existing.sourceTaskId(),
                            LongTermMemoryStatus.DELETED,
                            existing.createdAt(),
                            Instant.now()
                    ));
                    deleted++;
                }
            }
            memoryByUser.put(userId, records);
            return deleted;
        }
    }

    public static final class InMemoryLongTermMemoryJobRepository implements LongTermMemoryJobRepository {

        private final Map<String, MemoryExtractionJobRecord> jobsById = new LinkedHashMap<>();

        @Override
        public synchronized Optional<MemoryExtractionJobRecord> createQueuedIfAbsent(String userId,
                                                                                     String threadId,
                                                                                     String messageId,
                                                                                     String extractorVersion,
                                                                                     int eligibleTurnCount) {
            boolean exists = jobsById.values().stream()
                    .anyMatch(job -> job.userId().equals(userId)
                            && job.messageId().equals(messageId)
                            && job.extractorVersion().equals(extractorVersion));
            if (exists) {
                return Optional.empty();
            }
            Instant now = Instant.now();
            MemoryExtractionJobRecord job = new MemoryExtractionJobRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    threadId,
                    messageId,
                    extractorVersion,
                    MemoryExtractionJobStatus.QUEUED,
                    0,
                    null,
                    Math.max(1, eligibleTurnCount),
                    now,
                    now,
                    null,
                    null
            );
            jobsById.put(job.jobId(), job);
            return Optional.of(job);
        }

        @Override
        public synchronized Optional<MemoryExtractionJobRecord> findById(String jobId) {
            return Optional.ofNullable(jobsById.get(jobId));
        }

        @Override
        public synchronized Optional<MemoryExtractionJobRecord> findLatestSucceeded(String userId, String threadId, String extractorVersion) {
            return jobsById.values().stream()
                    .filter(job -> job.userId().equals(userId))
                    .filter(job -> job.threadId().equals(threadId))
                    .filter(job -> job.extractorVersion().equals(extractorVersion))
                    .filter(job -> job.status() == MemoryExtractionJobStatus.SUCCEEDED)
                    .max(Comparator.comparing(MemoryExtractionJobRecord::updatedAt));
        }

        @Override
        public synchronized boolean hasPendingJob(String userId, String threadId, String extractorVersion) {
            return jobsById.values().stream()
                    .anyMatch(job -> job.userId().equals(userId)
                            && job.threadId().equals(threadId)
                            && job.extractorVersion().equals(extractorVersion)
                            && (job.status() == MemoryExtractionJobStatus.QUEUED || job.status() == MemoryExtractionJobStatus.RUNNING));
        }

        @Override
        public synchronized Optional<MemoryExtractionJobRecord> markRunning(String jobId) {
            MemoryExtractionJobRecord existing = jobsById.get(jobId);
            if (existing == null || existing.status() != MemoryExtractionJobStatus.QUEUED) {
                return Optional.empty();
            }
            MemoryExtractionJobRecord updated = new MemoryExtractionJobRecord(
                    existing.jobId(),
                    existing.userId(),
                    existing.threadId(),
                    existing.messageId(),
                    existing.extractorVersion(),
                    MemoryExtractionJobStatus.RUNNING,
                    existing.attemptCount(),
                    existing.lastError(),
                    existing.eligibleTurnCount(),
                    existing.createdAt(),
                    Instant.now(),
                    Instant.now(),
                    existing.completedAt()
            );
            jobsById.put(jobId, updated);
            return Optional.of(updated);
        }

        @Override
        public synchronized MemoryExtractionJobRecord markSucceeded(String jobId) {
            MemoryExtractionJobRecord existing = jobsById.get(jobId);
            Instant now = Instant.now();
            MemoryExtractionJobRecord updated = new MemoryExtractionJobRecord(
                    existing.jobId(),
                    existing.userId(),
                    existing.threadId(),
                    existing.messageId(),
                    existing.extractorVersion(),
                    MemoryExtractionJobStatus.SUCCEEDED,
                    existing.attemptCount(),
                    null,
                    existing.eligibleTurnCount(),
                    existing.createdAt(),
                    now,
                    existing.startedAt(),
                    now
            );
            jobsById.put(jobId, updated);
            return updated;
        }

        @Override
        public synchronized MemoryExtractionJobRecord markFailure(String jobId, String lastError, boolean terminal) {
            MemoryExtractionJobRecord existing = jobsById.get(jobId);
            Instant now = Instant.now();
            MemoryExtractionJobRecord updated = new MemoryExtractionJobRecord(
                    existing.jobId(),
                    existing.userId(),
                    existing.threadId(),
                    existing.messageId(),
                    existing.extractorVersion(),
                    terminal ? MemoryExtractionJobStatus.FAILED : MemoryExtractionJobStatus.QUEUED,
                    existing.attemptCount() + 1,
                    lastError,
                    existing.eligibleTurnCount(),
                    existing.createdAt(),
                    now,
                    null,
                    terminal ? now : null
            );
            jobsById.put(jobId, updated);
            return updated;
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            jobsById.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId)
                    && entry.getValue().threadId().equals(threadId));
        }
    }

    private record ThreadKey(String userId, String threadId) {
    }

    private record UserTaskKey(String userId, String taskId) {
    }
}
