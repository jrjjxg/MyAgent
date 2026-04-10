package com.xg.platform.agent.core.application;

import com.xg.platform.contracts.memory.CachedThreadMemoryRecord;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.memory.port.ThreadMemoryViewCache;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;
import com.xg.platform.workspace.application.ThreadService;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ConversationMemoryService {

    private final ThreadService threadRuntimeService;
    private final MessageRepository messageRepository;
    private final ThreadMemorySnapshotRepository threadMemorySnapshotRepository;
    private final ThreadMemoryViewCache threadMemoryViewCache;
    private final ShortTermMemoryProjectionService shortTermMemoryProjectionService;
    private final int windowSize;
    private final boolean readModelAsync;

    public ConversationMemoryService(ThreadService threadRuntimeService,
                                     MessageRepository messageRepository,
                                     ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                     ThreadMemoryViewCache threadMemoryViewCache,
                                     ShortTermMemoryProjectionService shortTermMemoryProjectionService,
                                     int windowSize,
                                     boolean readModelAsync) {
        this.threadRuntimeService = threadRuntimeService;
        this.messageRepository = messageRepository;
        this.threadMemorySnapshotRepository = threadMemorySnapshotRepository;
        this.threadMemoryViewCache = threadMemoryViewCache;
        this.shortTermMemoryProjectionService = shortTermMemoryProjectionService;
        this.windowSize = Math.max(1, windowSize);
        this.readModelAsync = readModelAsync;
    }

    public ThreadMemoryView threadMemoryView(String userId, String threadId) {
        threadRuntimeService.getThread(userId, threadId);
        if (!readModelAsync) {
            return shortTermMemoryProjectionService.refreshThreadMemoryView(userId, threadId);
        }
        List<MessageRecord> recentMessages = messageRepository.listRecentMessages(userId, threadId, windowSize);
        String recentEndMessageId = recentMessages.isEmpty() ? null : recentMessages.get(recentMessages.size() - 1).messageId();
        ThreadMemorySnapshotRecord snapshot = threadMemorySnapshotRepository.findByThread(userId, threadId).orElse(null);
        if (snapshot == null) {
            return new ThreadMemoryView(
                    threadId,
                    recentMessages.isEmpty() ? "No conversation memory yet." : "",
                    recentMessages,
                    List.of(),
                    null,
                    null,
                    null
            );
        }
        CachedThreadMemoryRecord cachedMemory = threadMemoryViewCache.get(userId, threadId).orElse(null);
        MemoryBase memoryBase;
        if (cachedMemory != null && isCacheConsistent(cachedMemory, snapshot)) {
            memoryBase = MemoryBase.fromCache(cachedMemory);
        } else {
            memoryBase = MemoryBase.fromSnapshot(snapshot);
            threadMemoryViewCache.put(userId, new CachedThreadMemoryRecord(
                    threadId,
                    snapshot.summary(),
                    recentMessages,
                    snapshot.pendingHistoricalMessages(),
                    snapshot.activeDraftId(),
                    snapshot.activeTaskId(),
                    snapshot.taskStage(),
                    snapshot.lastCompactedMessageId(),
                    snapshot.recentEndMessageId(),
                    snapshot.recentWindowSize()
            ));
        }
        List<MessageRecord> pendingHistoricalMessages = sameMessage(snapshot.recentEndMessageId(), recentEndMessageId)
                ? memoryBase.pendingHistoricalMessages()
                : mergePendingMessages(
                        memoryBase.pendingHistoricalMessages(),
                        bridgePendingMessages(userId, threadId, snapshot.lastCompactedMessageId(), recentMessages)
                );
        return new ThreadMemoryView(
                threadId,
                displaySummary(memoryBase.summary(), recentMessages.isEmpty()),
                recentMessages,
                pendingHistoricalMessages,
                memoryBase.activeDraftId(),
                memoryBase.activeTaskId(),
                memoryBase.taskStage()
        );
    }

    private boolean isCacheConsistent(CachedThreadMemoryRecord cachedMemory, ThreadMemorySnapshotRecord snapshot) {
        return sameMessage(cachedMemory.lastCompactedMessageId(), snapshot.lastCompactedMessageId())
                && sameMessage(cachedMemory.recentEndMessageId(), snapshot.recentEndMessageId())
                && cachedMemory.recentWindowSize() == snapshot.recentWindowSize()
                && sameValue(cachedMemory.summary(), snapshot.summary())
                && sameMessages(cachedMemory.pendingHistoricalMessages(), snapshot.pendingHistoricalMessages())
                && sameValue(cachedMemory.activeDraftId(), snapshot.activeDraftId())
                && sameValue(cachedMemory.activeTaskId(), snapshot.activeTaskId())
                && sameValue(cachedMemory.taskStage(), snapshot.taskStage());
    }

    private List<MessageRecord> bridgePendingMessages(String userId,
                                                      String threadId,
                                                      String lastCompactedMessageId,
                                                      List<MessageRecord> recentMessages) {
        List<MessageRecord> uncompactedMessages = messageRepository.listMessagesAfter(userId, threadId, lastCompactedMessageId);
        if (uncompactedMessages.isEmpty()) {
            return List.of();
        }
        Set<String> recentIds = recentMessages.stream()
                .map(MessageRecord::messageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return uncompactedMessages.stream()
                .filter(message -> message.messageId() != null)
                .filter(message -> !recentIds.contains(message.messageId()))
                .toList();
    }

    private List<MessageRecord> mergePendingMessages(List<MessageRecord> persistedPendingMessages,
                                                     List<MessageRecord> bridgeMessages) {
        if (persistedPendingMessages.isEmpty()) {
            return List.copyOf(bridgeMessages);
        }
        if (bridgeMessages.isEmpty()) {
            return List.copyOf(persistedPendingMessages);
        }
        java.util.ArrayList<MessageRecord> merged = new java.util.ArrayList<>(persistedPendingMessages.size() + bridgeMessages.size());
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (MessageRecord message : persistedPendingMessages) {
            if (message.messageId() != null && seen.add(message.messageId())) {
                merged.add(message);
            }
        }
        for (MessageRecord message : bridgeMessages) {
            if (message.messageId() != null && seen.add(message.messageId())) {
                merged.add(message);
            }
        }
        return List.copyOf(merged);
    }

    private boolean sameValue(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private boolean sameMessage(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private boolean sameMessages(List<MessageRecord> left, List<MessageRecord> right) {
        return List.copyOf(left == null ? List.of() : left).equals(List.copyOf(right == null ? List.of() : right));
    }

    private String displaySummary(String summary, boolean noMessages) {
        if (noMessages) {
            return "No conversation memory yet.";
        }
        return summary == null ? "" : summary.trim();
    }

    private record MemoryBase(
            String summary,
            List<MessageRecord> pendingHistoricalMessages,
            String activeDraftId,
            String activeTaskId,
            String taskStage
    ) {
        private static MemoryBase fromCache(CachedThreadMemoryRecord cachedMemory) {
            return new MemoryBase(
                    cachedMemory.summary(),
                    cachedMemory.pendingHistoricalMessages(),
                    cachedMemory.activeDraftId(),
                    cachedMemory.activeTaskId(),
                    cachedMemory.taskStage()
            );
        }

        private static MemoryBase fromSnapshot(ThreadMemorySnapshotRecord snapshot) {
            return new MemoryBase(
                    snapshot.summary(),
                    snapshot.pendingHistoricalMessages(),
                    snapshot.activeDraftId(),
                    snapshot.activeTaskId(),
                    snapshot.taskStage()
            );
        }
    }
}
