package com.xg.platform.runtime;

import com.xg.platform.contracts.thread.ThreadRecord;
import com.xg.platform.contracts.thread.ThreadStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadRuntimeServiceTest {

    @Test
    void delegatesThreadLifecycleToRepository() {
        ThreadRuntimeService threadRuntimeService = new ThreadRuntimeService(new InMemoryThreadRepository());

        ThreadRecord created = threadRuntimeService.createThread("user-1", "workspace-1", "Research task");
        List<ThreadRecord> threads = threadRuntimeService.listThreads("user-1");

        assertThat(threads).hasSize(1);
        assertThat(threads.get(0)).isEqualTo(created);
        assertThat(threadRuntimeService.getThread("user-1", created.threadId())).isEqualTo(created);
        assertThat(threadRuntimeService.touchThread("user-1", created.threadId()).updatedAt())
                .isAfterOrEqualTo(created.updatedAt());
    }

    private static final class InMemoryThreadRepository implements ThreadRepository {

        private final List<ThreadRecord> threads = new ArrayList<>();

        @Override
        public ThreadRecord createThread(String userId, String workspaceId, String title) {
            Instant now = Instant.now();
            ThreadRecord record = new ThreadRecord(
                    UUID.randomUUID().toString(),
                    userId,
                    workspaceId,
                    title,
                    ThreadStatus.IDLE,
                    now,
                    now
            );
            threads.add(record);
            return record;
        }

        @Override
        public List<ThreadRecord> listThreads(String userId) {
            return threads.stream()
                    .filter(thread -> thread.userId().equals(userId))
                    .toList();
        }

        @Override
        public List<ThreadRecord> listThreads(String userId, String workspaceId) {
            return threads.stream()
                    .filter(thread -> thread.userId().equals(userId) && thread.workspaceId().equals(workspaceId))
                    .toList();
        }

        @Override
        public ThreadRecord getThread(String userId, String threadId) {
            return threads.stream()
                    .filter(thread -> thread.userId().equals(userId) && thread.threadId().equals(threadId))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("Thread not found: " + threadId));
        }

        @Override
        public ThreadRecord touchThread(String userId, String threadId) {
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
            threads.replaceAll(thread -> thread.threadId().equals(threadId) ? updated : thread);
            return updated;
        }

        @Override
        public void deleteThread(String userId, String threadId) {
            threads.removeIf(thread -> thread.userId().equals(userId) && thread.threadId().equals(threadId));
        }
    }
}
