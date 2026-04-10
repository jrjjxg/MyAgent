package com.xg.platform.agent.core.application;

import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryResearchDraftRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryTaskRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadMemorySnapshotRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadRepository;
import com.xg.platform.contracts.memory.CachedThreadMemoryRecord;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.MessageRole;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.memory.port.ThreadMemoryViewCache;
import com.xg.platform.workspace.application.ThreadService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryServiceTest {

    @Test
    void usesThreadMemoryViewCacheWhenSnapshotIsFresh() {
        TestContext context = new TestContext(2);
        MessageRecord message1 = context.appendMessage("m-1", MessageRole.USER, "hello");
        MessageRecord message2 = context.appendMessage("m-2", MessageRole.ASSISTANT, "world");
        Instant updatedAt = Instant.now();
        context.threadMemorySnapshotRepository.save(context.userId, ThreadMemorySnapshotRecord.withoutActiveSkillIds(
                context.threadId,
                context.userId,
                "cached full summary",
                message2.messageId(),
                List.of(),
                message2.messageId(),
                2,
                "draft-1",
                "task-1",
                "researching",
                updatedAt
        ));
        context.threadMemoryViewCache.put(context.userId, new CachedThreadMemoryRecord(
                context.threadId,
                "cached full summary",
                List.of(message1, message2),
                List.of(),
                "draft-1",
                "task-1",
                "researching",
                message2.messageId(),
                message2.messageId(),
                2
        ));

        var view = context.conversationMemoryService.threadMemoryView(context.userId, context.threadId);

        assertThat(view.summary()).isEqualTo("cached full summary");
        assertThat(view.pendingHistoricalMessages()).isEmpty();
        assertThat(view.activeDraftId()).isEqualTo("draft-1");
        assertThat(view.activeTaskId()).isEqualTo("task-1");
        assertThat(view.taskStage()).isEqualTo("researching");
        assertThat(view.recentMessages()).extracting(MessageRecord::messageId).containsExactly("m-1", "m-2");
        assertThat(context.messageRepository.listRecentMessagesCalls()).isEqualTo(1);
        assertThat(context.messageRepository.listMessagesCalls()).isZero();
        assertThat(context.messageRepository.listMessagesAfterCalls()).isZero();
    }

    @Test
    void bridgesPendingHistoryWhenSnapshotLagsBehindLatestRecentWindow() {
        TestContext context = new TestContext(1);
        MessageRecord message1 = context.appendMessage("m-1", MessageRole.USER, "first");
        MessageRecord message2 = context.appendMessage("m-2", MessageRole.ASSISTANT, "second");
        context.appendMessage("m-3", MessageRole.USER, "third");
        context.threadMemorySnapshotRepository.save(context.userId, ThreadMemorySnapshotRecord.withoutActiveSkillIds(
                context.threadId,
                context.userId,
                "persisted summary",
                message1.messageId(),
                List.of(),
                message2.messageId(),
                1,
                null,
                null,
                null,
                Instant.now()
        ));

        var view = context.conversationMemoryService.threadMemoryView(context.userId, context.threadId);

        assertThat(view.summary()).isEqualTo("persisted summary");
        assertThat(view.recentMessages()).extracting(MessageRecord::messageId).containsExactly("m-3");
        assertThat(view.pendingHistoricalMessages()).extracting(MessageRecord::messageId).containsExactly("m-2");
        assertThat(context.messageRepository.listRecentMessagesCalls()).isEqualTo(1);
        assertThat(context.messageRepository.listMessagesCalls()).isZero();
        assertThat(context.messageRepository.listMessagesAfterCalls()).isEqualTo(1);
        assertThat(context.threadMemorySnapshotRepository.findByThread(context.userId, context.threadId))
                .map(ThreadMemorySnapshotRecord::lastCompactedMessageId)
                .contains("m-1");
    }

    @Test
    void backfillsCacheFromSnapshotWhenCacheMisses() {
        TestContext context = new TestContext(2);
        context.appendMessage("m-1", MessageRole.USER, "first");
        context.appendMessage("m-2", MessageRole.ASSISTANT, "second");
        MessageRecord message3 = context.appendMessage("m-3", MessageRole.USER, "third");
        context.threadMemorySnapshotRepository.save(context.userId, ThreadMemorySnapshotRecord.withoutActiveSkillIds(
                context.threadId,
                context.userId,
                "fresh summary",
                "m-1",
                List.of(),
                message3.messageId(),
                2,
                null,
                null,
                null,
                Instant.now()
        ));

        var view = context.conversationMemoryService.threadMemoryView(context.userId, context.threadId);

        assertThat(view.summary()).isEqualTo("fresh summary");
        assertThat(view.recentMessages()).extracting(MessageRecord::messageId).containsExactly("m-2", "m-3");
        assertThat(view.pendingHistoricalMessages()).isEmpty();
        assertThat(context.messageRepository.listRecentMessagesCalls()).isEqualTo(1);
        assertThat(context.messageRepository.listMessagesCalls()).isZero();
        assertThat(context.messageRepository.listMessagesAfterCalls()).isZero();
        assertThat(context.threadMemoryViewCache.get(context.userId, context.threadId))
                .get()
                .satisfies(record -> {
                    assertThat(record.summary()).isEqualTo("fresh summary");
                    assertThat(record.pendingHistoricalMessages()).isEmpty();
                    assertThat(record.recentMessages()).extracting(MessageRecord::messageId).containsExactly("m-2", "m-3");
                    assertThat(record.recentEndMessageId()).isEqualTo("m-3");
                    assertThat(record.recentWindowSize()).isEqualTo(2);
                });
    }

    @Test
    void returnsEmptyMemoryViewForThreadWithoutMessages() {
        TestContext context = new TestContext(2);

        var view = context.conversationMemoryService.threadMemoryView(context.userId, context.threadId);

        assertThat(view.recentMessages()).isEmpty();
        assertThat(view.pendingHistoricalMessages()).isEmpty();
        assertThat(view.summary()).isEqualTo("No conversation memory yet.");
        assertThat(context.messageRepository.listRecentMessagesCalls()).isEqualTo(1);
        assertThat(context.messageRepository.listMessagesCalls()).isZero();
    }

    private static final class TestContext {
        private final String userId = "user-1";
        private final String threadId;
        private final CountingMessageRepository messageRepository = new CountingMessageRepository();
        private final InMemoryThreadMemorySnapshotRepository threadMemorySnapshotRepository = new InMemoryThreadMemorySnapshotRepository();
        private final MapThreadMemoryViewCache threadMemoryViewCache = new MapThreadMemoryViewCache();
        private final ConversationMemoryService conversationMemoryService;

        private TestContext(int windowSize) {
            ThreadService threadRuntimeService = new ThreadService(new InMemoryThreadRepository());
            this.threadId = threadRuntimeService.createThread(userId, "workspace-1", "Thread").threadId();
            ShortTermMemoryProjectionService projectionService = ShortTermMemoryProjectionService.withDefaultCompressor(
                    messageRepository,
                    threadMemorySnapshotRepository,
                    threadMemoryViewCache,
                    new InMemoryResearchDraftRepository(),
                    new InMemoryTaskRepository(),
                    windowSize
            );
            this.conversationMemoryService = new ConversationMemoryService(
                    threadRuntimeService,
                    messageRepository,
                    threadMemorySnapshotRepository,
                    threadMemoryViewCache,
                    projectionService,
                    windowSize,
                    true
            );
        }

        private MessageRecord appendMessage(String messageId, MessageRole role, String content) {
            MessageRecord message = new MessageRecord(
                    messageId,
                    threadId,
                    role,
                    content,
                    InteractionMode.CHAT,
                    "run-1",
                    null,
                    Instant.now()
            );
            messageRepository.append(userId, message);
            return message;
        }
    }

    private static final class CountingMessageRepository implements MessageRepository {
        private final Map<String, List<MessageRecord>> messagesByThread = new LinkedHashMap<>();
        private int listMessagesCalls;
        private int listRecentMessagesCalls;
        private int listMessagesAfterCalls;

        @Override
        public synchronized MessageRecord append(String userId, MessageRecord messageRecord) {
            messagesByThread.computeIfAbsent(key(userId, messageRecord.threadId()), ignored -> new ArrayList<>())
                    .add(messageRecord);
            return messageRecord;
        }

        @Override
        public synchronized List<MessageRecord> listMessages(String userId, String threadId) {
            listMessagesCalls++;
            return List.copyOf(messagesByThread.getOrDefault(key(userId, threadId), List.of()));
        }

        @Override
        public synchronized List<MessageRecord> listRecentMessages(String userId, String threadId, int limit) {
            listRecentMessagesCalls++;
            List<MessageRecord> allMessages = messagesByThread.getOrDefault(key(userId, threadId), List.of());
            int safeLimit = Math.max(1, limit);
            int fromIndex = Math.max(0, allMessages.size() - safeLimit);
            return List.copyOf(allMessages.subList(fromIndex, allMessages.size()));
        }

        @Override
        public synchronized List<MessageRecord> listMessagesAfter(String userId, String threadId, String messageIdExclusive) {
            listMessagesAfterCalls++;
            List<MessageRecord> allMessages = messagesByThread.getOrDefault(key(userId, threadId), List.of());
            if (messageIdExclusive == null || messageIdExclusive.isBlank()) {
                return List.copyOf(allMessages);
            }
            for (int index = 0; index < allMessages.size(); index++) {
                if (messageIdExclusive.equals(allMessages.get(index).messageId())) {
                    return List.copyOf(allMessages.subList(index + 1, allMessages.size()));
                }
            }
            return List.copyOf(allMessages);
        }

        @Override
        public synchronized Optional<String> findLatestMessageId(String userId, String threadId) {
            List<MessageRecord> messages = messagesByThread.getOrDefault(key(userId, threadId), List.of());
            if (messages.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(messages.get(messages.size() - 1).messageId());
        }

        @Override
        public synchronized Optional<MessageRecord> findById(String userId, String threadId, String messageId) {
            return messagesByThread.getOrDefault(key(userId, threadId), List.of()).stream()
                    .filter(message -> message.messageId().equals(messageId))
                    .findFirst();
        }

        @Override
        public synchronized void deleteByThread(String userId, String threadId) {
            messagesByThread.remove(key(userId, threadId));
        }

        private int listMessagesCalls() {
            return listMessagesCalls;
        }

        private int listRecentMessagesCalls() {
            return listRecentMessagesCalls;
        }

        private int listMessagesAfterCalls() {
            return listMessagesAfterCalls;
        }

        private String key(String userId, String threadId) {
            return userId + "::" + threadId;
        }
    }

    private static final class MapThreadMemoryViewCache implements ThreadMemoryViewCache {
        private final Map<String, CachedThreadMemoryRecord> values = new LinkedHashMap<>();

        @Override
        public synchronized Optional<CachedThreadMemoryRecord> get(String userId, String threadId) {
            return Optional.ofNullable(values.get(key(userId, threadId)));
        }

        @Override
        public synchronized void put(String userId, CachedThreadMemoryRecord record) {
            values.put(key(userId, record.threadId()), record);
        }

        private String key(String userId, String threadId) {
            return userId + "::" + threadId;
        }
    }
}
