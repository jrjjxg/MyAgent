package com.xg.platform.agent.core.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryMessageRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryResearchDraftRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryTaskRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadMemorySnapshotRepository;
import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;
import com.xg.platform.contracts.memory.MemoryExtractionJobStatus;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.MessageRole;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.memory.application.NoOpThreadMemoryViewCache;
import com.xg.platform.memory.port.LongTermMemoryExtractionRequest;
import com.xg.platform.memory.port.LongTermMemoryJobDispatcher;
import com.xg.platform.memory.port.LongTermMemoryJobRepository;
import com.xg.platform.memory.port.MemoryEventPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongTermMemoryJobFlowTest {

    @Test
    void schedulerDispatchesOnlyNewJobs() {
        InMemoryLongTermMemoryJobRepository repository = new InMemoryLongTermMemoryJobRepository();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        InMemoryMessageRepository messageRepository = new InMemoryMessageRepository();
        messageRepository.append("user-1", new MessageRecord("message-1", "thread-1", MessageRole.ASSISTANT, "ack", InteractionMode.CHAT, "run-1", null, Instant.now()));
        LongTermMemoryJobScheduler scheduler = new LongTermMemoryJobScheduler(repository, dispatcher, messageRepository, "v1", 1);

        scheduler.schedule("user-1", "thread-1", "message-1");
        scheduler.schedule("user-1", "thread-1", "message-1");

        assertThat(dispatcher.lastRequest).isNotNull();
        assertThat(dispatcher.dispatchCount).isEqualTo(1);
        assertThat(repository.findById(dispatcher.lastRequest.jobId()))
                .map(MemoryExtractionJobRecord::status)
                .contains(MemoryExtractionJobStatus.QUEUED);
    }

    @Test
    void schedulerWaitsUntilConfiguredTurnIntervalBeforeDispatching() {
        InMemoryLongTermMemoryJobRepository repository = new InMemoryLongTermMemoryJobRepository();
        RecordingDispatcher dispatcher = new RecordingDispatcher();
        InMemoryMessageRepository messageRepository = new InMemoryMessageRepository();
        messageRepository.append("user-1", new MessageRecord("message-1", "thread-1", MessageRole.USER, "hello", InteractionMode.CHAT, "run-1", null, Instant.now()));
        messageRepository.append("user-1", new MessageRecord("message-2", "thread-1", MessageRole.ASSISTANT, "one", InteractionMode.CHAT, "run-1", null, Instant.now().plusSeconds(1)));
        messageRepository.append("user-1", new MessageRecord("message-3", "thread-1", MessageRole.USER, "again", InteractionMode.CHAT, "run-2", null, Instant.now().plusSeconds(2)));
        messageRepository.append("user-1", new MessageRecord("message-4", "thread-1", MessageRole.ASSISTANT, "two", InteractionMode.CHAT, "run-2", null, Instant.now().plusSeconds(3)));

        LongTermMemoryJobScheduler scheduler = new LongTermMemoryJobScheduler(repository, dispatcher, messageRepository, "v1", 3);

        scheduler.schedule("user-1", "thread-1", "message-2");
        scheduler.schedule("user-1", "thread-1", "message-4");

        assertThat(dispatcher.lastRequest).isNull();
        assertThat(dispatcher.dispatchCount).isZero();

        messageRepository.append("user-1", new MessageRecord("message-5", "thread-1", MessageRole.USER, "third", InteractionMode.CHAT, "run-3", null, Instant.now().plusSeconds(4)));
        messageRepository.append("user-1", new MessageRecord("message-6", "thread-1", MessageRole.ASSISTANT, "three", InteractionMode.CHAT, "run-3", null, Instant.now().plusSeconds(5)));

        scheduler.schedule("user-1", "thread-1", "message-6");

        assertThat(dispatcher.dispatchCount).isEqualTo(1);
        assertThat(dispatcher.lastRequest).isNotNull();
        assertThat(repository.findById(dispatcher.lastRequest.jobId()))
                .map(MemoryExtractionJobRecord::eligibleTurnCount)
                .contains(3);
    }

    @Test
    void processorMarksJobsSucceededWhenExtractionCompletes() {
        InMemoryLongTermMemoryJobRepository repository = new InMemoryLongTermMemoryJobRepository();
        LongTermMemoryExtractionRequest request = repository.createQueuedIfAbsent("user-1", "thread-1", "message-1", "v1", 5)
                .map(record -> new LongTermMemoryExtractionRequest(
                        record.jobId(),
                        record.userId(),
                        record.threadId(),
                        record.messageId(),
                        record.extractorVersion()
                ))
                .orElseThrow();
        StubLongTermMemoryExtractionService extractionService = new StubLongTermMemoryExtractionService(null);
        DefaultLongTermMemoryJobProcessor processor = new DefaultLongTermMemoryJobProcessor(repository, extractionService, 3);

        processor.process(request);

        assertThat(extractionService.invocations).isEqualTo(1);
        assertThat(repository.findById(request.jobId()))
                .map(MemoryExtractionJobRecord::status)
                .contains(MemoryExtractionJobStatus.SUCCEEDED);
    }

    @Test
    void processorRetriesAndEventuallyMarksJobsFailed() {
        InMemoryLongTermMemoryJobRepository repository = new InMemoryLongTermMemoryJobRepository();
        LongTermMemoryExtractionRequest request = repository.createQueuedIfAbsent("user-1", "thread-1", "message-1", "v1", 5)
                .map(record -> new LongTermMemoryExtractionRequest(
                        record.jobId(),
                        record.userId(),
                        record.threadId(),
                        record.messageId(),
                        record.extractorVersion()
                ))
                .orElseThrow();
        StubLongTermMemoryExtractionService extractionService =
                new StubLongTermMemoryExtractionService(new IllegalStateException("boom"));
        DefaultLongTermMemoryJobProcessor processor = new DefaultLongTermMemoryJobProcessor(repository, extractionService, 3);

        assertThatThrownBy(() -> processor.process(request)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> processor.process(request)).isInstanceOf(IllegalStateException.class);
        processor.process(request);

        assertThat(extractionService.invocations).isEqualTo(3);
        assertThat(repository.findById(request.jobId()))
                .map(MemoryExtractionJobRecord::status)
                .contains(MemoryExtractionJobStatus.FAILED);
        assertThat(repository.findById(request.jobId()))
                .map(MemoryExtractionJobRecord::attemptCount)
                .contains(3);
    }

    @Test
    void memoryEventProcessorSchedulesLongTermJobsWithoutRunningExtractionInline() {
        TrackingShortTermMemoryProjectionService shortTermMemoryProjectionService = new TrackingShortTermMemoryProjectionService();
        TrackingLongTermMemoryJobScheduler longTermMemoryJobScheduler = new TrackingLongTermMemoryJobScheduler();
        try (DefaultMemoryEventProcessor processor =
                     new DefaultMemoryEventProcessor(shortTermMemoryProjectionService, longTermMemoryJobScheduler, 0L)) {
            processor.process(new MemoryEventPayload(
                    "message.completed",
                    "user-1",
                    "thread-1",
                    null,
                    "message-1",
                    Instant.now()
            ));

            waitUntil(() -> shortTermMemoryProjectionService.projectThreadMemoryCalls == 1, 1_000L);

            assertThat(shortTermMemoryProjectionService.projectThreadMemoryCalls).isEqualTo(1);
            assertThat(shortTermMemoryProjectionService.lastUserId).isEqualTo("user-1");
            assertThat(shortTermMemoryProjectionService.lastThreadId).isEqualTo("thread-1");
            assertThat(longTermMemoryJobScheduler.scheduleCalls).isEqualTo(1);
            assertThat(longTermMemoryJobScheduler.lastUserId).isEqualTo("user-1");
            assertThat(longTermMemoryJobScheduler.lastThreadId).isEqualTo("thread-1");
            assertThat(longTermMemoryJobScheduler.lastMessageId).isEqualTo("message-1");
        }
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for async projection", exception);
            }
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static final class RecordingDispatcher implements LongTermMemoryJobDispatcher {
        private int dispatchCount;
        private LongTermMemoryExtractionRequest lastRequest;

        @Override
        public void dispatch(LongTermMemoryExtractionRequest request) {
            dispatchCount++;
            lastRequest = request;
        }
    }

    private static final class InMemoryLongTermMemoryJobRepository implements LongTermMemoryJobRepository {
        private final Map<String, MemoryExtractionJobRecord> jobsById = new LinkedHashMap<>();
        private final Map<String, String> uniqueKeyToJobId = new LinkedHashMap<>();

        @Override
        public synchronized Optional<MemoryExtractionJobRecord> createQueuedIfAbsent(String userId,
                                                                                     String threadId,
                                                                                     String messageId,
                                                                                     String extractorVersion,
                                                                                     int eligibleTurnCount) {
            String key = uniqueKey(userId, messageId, extractorVersion);
            if (uniqueKeyToJobId.containsKey(key)) {
                return Optional.empty();
            }
            Instant now = Instant.now();
            MemoryExtractionJobRecord record = new MemoryExtractionJobRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    threadId,
                    messageId,
                    extractorVersion,
                    MemoryExtractionJobStatus.QUEUED,
                    0,
                    null,
                    eligibleTurnCount,
                    now,
                    now,
                    null,
                    null
            );
            jobsById.put(record.jobId(), record);
            uniqueKeyToJobId.put(key, record.jobId());
            return Optional.of(record);
        }

        @Override
        public synchronized Optional<MemoryExtractionJobRecord> findById(String jobId) {
            return Optional.ofNullable(jobsById.get(jobId));
        }

        @Override
        public synchronized Optional<MemoryExtractionJobRecord> findLatestSucceeded(String userId,
                                                                                    String threadId,
                                                                                    String extractorVersion) {
            return jobsById.values().stream()
                    .filter(job -> job.userId().equals(userId)
                            && job.threadId().equals(threadId)
                            && job.extractorVersion().equals(extractorVersion)
                            && job.status() == MemoryExtractionJobStatus.SUCCEEDED)
                    .reduce((first, second) -> second);
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
            MemoryExtractionJobRecord current = jobsById.get(jobId);
            if (current == null || current.status() != MemoryExtractionJobStatus.QUEUED) {
                return Optional.empty();
            }
            MemoryExtractionJobRecord updated = new MemoryExtractionJobRecord(
                    current.jobId(),
                    current.userId(),
                    current.threadId(),
                    current.messageId(),
                    current.extractorVersion(),
                    MemoryExtractionJobStatus.RUNNING,
                    current.attemptCount(),
                    current.lastError(),
                    current.eligibleTurnCount(),
                    current.createdAt(),
                    Instant.now(),
                    Instant.now(),
                    current.completedAt()
            );
            jobsById.put(jobId, updated);
            return Optional.of(updated);
        }

        @Override
        public synchronized MemoryExtractionJobRecord markSucceeded(String jobId) {
            MemoryExtractionJobRecord current = jobsById.get(jobId);
            Instant now = Instant.now();
            MemoryExtractionJobRecord updated = new MemoryExtractionJobRecord(
                    current.jobId(),
                    current.userId(),
                    current.threadId(),
                    current.messageId(),
                    current.extractorVersion(),
                    MemoryExtractionJobStatus.SUCCEEDED,
                    current.attemptCount(),
                    null,
                    current.eligibleTurnCount(),
                    current.createdAt(),
                    now,
                    current.startedAt(),
                    now
            );
            jobsById.put(jobId, updated);
            return updated;
        }

        @Override
        public synchronized MemoryExtractionJobRecord markFailure(String jobId, String lastError, boolean terminal) {
            MemoryExtractionJobRecord current = jobsById.get(jobId);
            Instant now = Instant.now();
            MemoryExtractionJobRecord updated = new MemoryExtractionJobRecord(
                    current.jobId(),
                    current.userId(),
                    current.threadId(),
                    current.messageId(),
                    current.extractorVersion(),
                    terminal ? MemoryExtractionJobStatus.FAILED : MemoryExtractionJobStatus.QUEUED,
                    current.attemptCount() + 1,
                    lastError,
                    current.eligibleTurnCount(),
                    current.createdAt(),
                    now,
                    null,
                    terminal ? now : null
            );
            jobsById.put(jobId, updated);
            return updated;
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            List<String> deletedJobIds = jobsById.values().stream()
                    .filter(job -> job.userId().equals(userId) && job.threadId().equals(threadId))
                    .map(MemoryExtractionJobRecord::jobId)
                    .toList();
            jobsById.keySet().removeAll(deletedJobIds);
            uniqueKeyToJobId.entrySet().removeIf(entry -> deletedJobIds.contains(entry.getValue()));
        }

        private String uniqueKey(String userId, String messageId, String extractorVersion) {
            return userId + "::" + messageId + "::" + extractorVersion;
        }
    }

    private static final class StubLongTermMemoryExtractionService extends LongTermMemoryExtractionService {
        private final RuntimeException failure;
        private int invocations;

        private StubLongTermMemoryExtractionService(RuntimeException failure) {
            super(null, null, new InMemoryLongTermMemoryJobRepository(), null, new ObjectMapper(), "provider", "model", "v1", 24, false);
            this.failure = failure;
        }

        @Override
        public ExtractionOutcome extractFromCompletedMessage(String userId, String threadId, String messageId) {
            invocations++;
            if (failure != null) {
                throw failure;
            }
            return new ExtractionOutcome(1, false, "processed");
        }
    }

    private static final class TrackingShortTermMemoryProjectionService extends ShortTermMemoryProjectionService {
        private int projectThreadMemoryCalls;
        private String lastUserId;
        private String lastThreadId;

        private TrackingShortTermMemoryProjectionService() {
            super(
                    new InMemoryMessageRepository(),
                    new InMemoryThreadMemorySnapshotRepository(),
                    new NoOpThreadMemoryViewCache(),
                    SimpleConversationSummaryCompressor.defaults(),
                    new InMemoryResearchDraftRepository(),
                    new InMemoryTaskRepository(),
                    5
            );
        }

        @Override
        public ThreadMemoryView projectThreadMemory(String userId, String threadId) {
            projectThreadMemoryCalls++;
            lastUserId = userId;
            lastThreadId = threadId;
            return new ThreadMemoryView(threadId, "summary", java.util.List.of(), java.util.List.of(), null, null, null);
        }
    }

    private static final class TrackingLongTermMemoryJobScheduler extends LongTermMemoryJobScheduler {
        private int scheduleCalls;
        private String lastUserId;
        private String lastThreadId;
        private String lastMessageId;

        private TrackingLongTermMemoryJobScheduler() {
            super(new InMemoryLongTermMemoryJobRepository(), request -> {
            }, new InMemoryMessageRepository(), "v1", 5);
        }

        @Override
        public void schedule(String userId, String threadId, String messageId) {
            scheduleCalls++;
            lastUserId = userId;
            lastThreadId = threadId;
            lastMessageId = messageId;
        }
    }
}
