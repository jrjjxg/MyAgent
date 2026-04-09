package com.xg.platform.agent.core.application;

import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryMessageRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryResearchDraftRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryTaskRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadMemorySnapshotRepository;
import com.xg.platform.contracts.memory.CachedThreadMemoryRecord;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;
import com.xg.platform.runtime.ThreadMemoryViewCache;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShortTermMemoryProjectionServiceTest {

    @Test
    void keepsSummaryEmptyWhenMessagesDoNotExceedRecentWindow() {
        RecordingSummaryCompressor compressor = new RecordingSummaryCompressor();
        TestContext context = new TestContext(3, compressor);

        context.appendMessage("m-1", MessageRole.USER, "Need a concise summary.");
        context.appendMessage("m-2", MessageRole.ASSISTANT, "Confirmed.");

        ThreadMemoryView view = context.service.refreshThreadMemoryView(context.userId, context.threadId);

        assertThat(view.recentMessages()).extracting(MessageRecord::messageId).containsExactly("m-1", "m-2");
        assertThat(view.summary()).isEmpty();
        assertThat(view.pendingHistoricalMessages()).isEmpty();
        assertThat(compressor.bootstrapCalls).isZero();
        assertThat(compressor.extendCalls).isZero();
    }

    @Test
    void bootstrapsSummaryWhenMessagesFirstOverflowRecentWindow() {
        RecordingSummaryCompressor compressor = new RecordingSummaryCompressor();
        TestContext context = new TestContext(2, compressor);

        context.appendMessage("m-1", MessageRole.USER, "Need a concise summary.");
        context.appendMessage("m-2", MessageRole.ASSISTANT, "Confirmed.");
        context.appendMessage("m-3", MessageRole.USER, "Please also remember the Oracle database detail.");

        ThreadMemoryView view = context.service.refreshThreadMemoryView(context.userId, context.threadId);

        assertThat(view.recentMessages()).extracting(MessageRecord::messageId).containsExactly("m-2", "m-3");
        assertThat(view.summary()).isEqualTo("EXTEND[|USER:Need a concise summary.]");
        assertThat(view.pendingHistoricalMessages()).isEmpty();
        assertThat(compressor.bootstrapCalls).isZero();
        assertThat(compressor.extendCalls).isEqualTo(1);
        assertThat(compressor.lastExtendedMessages).extracting(MessageRecord::messageId).containsExactly("m-1");
        assertThat(context.snapshot().map(ThreadMemorySnapshotRecord::lastCompactedMessageId)).contains("m-1");
    }

    @Test
    void keepsSlidingMessagesInPendingUntilCompressionThresholdIsReached() {
        RecordingSummaryCompressor compressor = new RecordingSummaryCompressor();
        TestContext context = new TestContext(2, compressor);

        context.appendMessage("m-1", MessageRole.USER, "Need a concise summary.");
        context.appendMessage("m-2", MessageRole.ASSISTANT, "Confirmed.");
        context.appendMessage("m-3", MessageRole.USER, "Remember Oracle.");
        context.service.refreshThreadMemoryView(context.userId, context.threadId);

        context.appendMessage("m-4", MessageRole.ASSISTANT, "Oracle is confirmed.");

        ThreadMemoryView view = context.service.onMessageCompleted(context.userId, context.threadId);

        assertThat(view.recentMessages()).extracting(MessageRecord::messageId).containsExactly("m-3", "m-4");
        assertThat(view.summary()).isEqualTo("EXTEND[|USER:Need a concise summary.]");
        assertThat(view.pendingHistoricalMessages()).extracting(MessageRecord::messageId).containsExactly("m-2");
        assertThat(compressor.extendCalls).isEqualTo(1);
        assertThat(context.snapshot().map(ThreadMemorySnapshotRecord::pendingHistoricalMessages))
                .hasValueSatisfying(messages -> assertThat(messages).extracting(MessageRecord::messageId).containsExactly("m-2"));
    }

    @Test
    void compressesPendingHistoryOnceThresholdIsReached() {
        RecordingSummaryCompressor compressor = new RecordingSummaryCompressor();
        TestContext context = new TestContext(2, compressor);

        context.appendMessage("m-1", MessageRole.USER, "Need a concise summary.");
        context.appendMessage("m-2", MessageRole.ASSISTANT, "Confirmed.");
        context.appendMessage("m-3", MessageRole.USER, "Remember Oracle.");
        context.service.refreshThreadMemoryView(context.userId, context.threadId);

        for (int index = 4; index <= 15; index++) {
            MessageRole role = index % 2 == 0 ? MessageRole.ASSISTANT : MessageRole.USER;
            context.appendMessage("m-" + index, role, "message " + index);
        }

        ThreadMemoryView view = context.service.onMessageCompleted(context.userId, context.threadId);

        assertThat(view.recentMessages()).extracting(MessageRecord::messageId).containsExactly("m-14", "m-15");
        assertThat(view.pendingHistoricalMessages()).isEmpty();
        assertThat(compressor.extendCalls).isEqualTo(2);
        assertThat(compressor.lastExtendedMessages).extracting(MessageRecord::messageId)
                .containsExactly("m-2", "m-3", "m-4", "m-5", "m-6", "m-7", "m-8", "m-9", "m-10", "m-11", "m-12", "m-13");
        assertThat(context.snapshot().map(ThreadMemorySnapshotRecord::lastCompactedMessageId)).contains("m-13");
    }

    @Test
    void reusesExistingSummaryWhenOnlyTaskStateChanges() {
        RecordingSummaryCompressor compressor = new RecordingSummaryCompressor();
        TestContext context = new TestContext(2, compressor);

        context.appendMessage("m-1", MessageRole.USER, "Need a concise summary.");
        context.appendMessage("m-2", MessageRole.ASSISTANT, "Confirmed.");
        context.appendMessage("m-3", MessageRole.USER, "Remember Oracle.");
        context.service.refreshThreadMemoryView(context.userId, context.threadId);

        ThreadMemoryView view = context.service.onResearchBriefUpdated(context.userId, context.threadId);

        assertThat(view.summary()).isEqualTo("EXTEND[|USER:Need a concise summary.]");
        assertThat(view.pendingHistoricalMessages()).isEmpty();
        assertThat(compressor.extendCalls).isEqualTo(1);
    }

    @Test
    void preservesActiveSkillIdsWhenRefreshingSnapshot() {
        RecordingSummaryCompressor compressor = new RecordingSummaryCompressor();
        TestContext context = new TestContext(2, compressor);

        context.appendMessage("m-1", MessageRole.USER, "Need weather help.");
        context.appendMessage("m-2", MessageRole.ASSISTANT, "Confirmed.");
        context.threadMemorySnapshotRepository.save(context.userId, new ThreadMemorySnapshotRecord(
                context.threadId,
                context.userId,
                "persisted summary",
                "m-1",
                List.of(),
                "m-2",
                2,
                null,
                null,
                null,
                List.of("weather"),
                Instant.now()
        ));

        context.appendMessage("m-3", MessageRole.USER, "What about tomorrow?");

        context.service.onMessageCompleted(context.userId, context.threadId);

        assertThat(context.snapshot())
                .map(ThreadMemorySnapshotRecord::activeSkillIds)
                .hasValueSatisfying(activeSkillIds -> assertThat(activeSkillIds).containsExactly("weather"));
    }

    private static final class TestContext {
        private final String userId = "user-1";
        private final String threadId = "thread-1";
        private final InMemoryMessageRepository messageRepository = new InMemoryMessageRepository();
        private final InMemoryThreadMemorySnapshotRepository threadMemorySnapshotRepository = new InMemoryThreadMemorySnapshotRepository();
        private final ShortTermMemoryProjectionService service;

        private TestContext(int windowSize, ConversationSummaryCompressor compressor) {
            this.service = new ShortTermMemoryProjectionService(
                    messageRepository,
                    threadMemorySnapshotRepository,
                    new MapThreadMemoryViewCache(),
                    compressor,
                    new InMemoryResearchDraftRepository(),
                    new InMemoryTaskRepository(),
                    windowSize
            );
        }

        private void appendMessage(String messageId, MessageRole role, String content) {
            messageRepository.append(userId, new MessageRecord(
                    messageId,
                    threadId,
                    role,
                    content,
                    InteractionMode.CHAT,
                    "run-1",
                    null,
                    Instant.now()
            ));
        }

        private Optional<ThreadMemorySnapshotRecord> snapshot() {
            return threadMemorySnapshotRepository.findByThread(userId, threadId);
        }
    }

    private static final class RecordingSummaryCompressor implements ConversationSummaryCompressor {
        private int bootstrapCalls;
        private int extendCalls;
        private List<MessageRecord> lastExtendedMessages = List.of();
        private String lastUserId;

        @Override
        public String summarizeHistory(String userId, List<MessageRecord> historicalMessages) {
            bootstrapCalls++;
            lastUserId = userId;
            return "BOOTSTRAP[" + render(historicalMessages) + "]";
        }

        @Override
        public String extendSummary(String userId, String existingSummary, List<MessageRecord> newlyHistoricalMessages) {
            extendCalls++;
            lastUserId = userId;
            lastExtendedMessages = List.copyOf(newlyHistoricalMessages);
            return "EXTEND[" + existingSummary + "|" + render(newlyHistoricalMessages) + "]";
        }

        private String render(List<MessageRecord> messages) {
            return messages.stream()
                    .map(message -> message.role().name() + ":" + message.content())
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
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
