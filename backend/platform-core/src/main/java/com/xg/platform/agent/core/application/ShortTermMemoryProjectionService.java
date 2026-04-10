package com.xg.platform.agent.core.application;

import com.xg.platform.contracts.memory.CachedThreadMemoryRecord;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.research.ResearchDraftRecord;
import com.xg.platform.contracts.research.ResearchDraftStatus;
import com.xg.platform.contracts.shared.task.TaskRecord;
import com.xg.platform.contracts.shared.task.TaskStatus;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.memory.port.ThreadMemoryViewCache;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ShortTermMemoryProjectionService {

    private final MessageRepository messageRepository;
    private final ThreadMemorySnapshotRepository threadMemorySnapshotRepository;
    private final ThreadMemoryViewCache threadMemoryViewCache;
    private final ConversationSummaryCompressor conversationSummaryCompressor;
    private final ResearchDraftRepository researchDraftRepository;
    private final TaskRepository taskRepository;
    private final int windowSize;

    public static ShortTermMemoryProjectionService withDefaultCompressor(MessageRepository messageRepository,
                                                                         ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                                                         ThreadMemoryViewCache threadMemoryViewCache,
                                                                         ResearchDraftRepository researchDraftRepository,
                                                                         TaskRepository taskRepository,
                                                                         int windowSize) {
        return new ShortTermMemoryProjectionService(
                messageRepository,
                threadMemorySnapshotRepository,
                threadMemoryViewCache,
                SimpleConversationSummaryCompressor.defaults(),
                researchDraftRepository,
                taskRepository,
                windowSize
        );
    }

    public ShortTermMemoryProjectionService(MessageRepository messageRepository,
                                            ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                            ThreadMemoryViewCache threadMemoryViewCache,
                                            ConversationSummaryCompressor conversationSummaryCompressor,
                                            ResearchDraftRepository researchDraftRepository,
                                            TaskRepository taskRepository,
                                            int windowSize) {
        this.messageRepository = messageRepository;
        this.threadMemorySnapshotRepository = threadMemorySnapshotRepository;
        this.threadMemoryViewCache = threadMemoryViewCache;
        this.conversationSummaryCompressor = conversationSummaryCompressor == null
                ? SimpleConversationSummaryCompressor.defaults()
                : conversationSummaryCompressor;
        this.researchDraftRepository = researchDraftRepository;
        this.taskRepository = taskRepository;
        this.windowSize = Math.max(1, windowSize);
    }

    public ThreadMemoryView refreshThreadMemoryView(String userId, String threadId) {
        return projectThreadMemory(userId, threadId);
    }

    public ThreadMemoryView onMessageCompleted(String userId, String threadId) {
        return projectThreadMemory(userId, threadId);
    }

    public ThreadMemoryView onResearchBriefUpdated(String userId, String threadId) {
        return projectThreadMemory(userId, threadId);
    }

    public ThreadMemoryView onTaskCreated(String userId, String threadId, String taskId) {
        return projectThreadMemory(userId, threadId);
    }

    public ThreadMemoryView onTaskStageChanged(String userId, String threadId, String taskId) {
        return projectThreadMemory(userId, threadId);
    }

    public ThreadMemoryView onTaskTerminal(String userId, String threadId) {
        return projectThreadMemory(userId, threadId);
    }

    public ThreadMemoryView projectThreadMemory(String userId, String threadId) {
        return project(
                userId,
                threadId,
                currentActiveDraftId(userId, threadId),
                currentActiveTaskId(userId, threadId),
                currentTaskStage(userId, threadId)
        );
    }

    private ThreadMemoryView project(String userId,
                                     String threadId,
                                     String activeDraftId,
                                     String activeTaskId,
                                     String taskStage) {
        List<MessageRecord> allMessages = messageRepository.listMessages(userId, threadId);
        ThreadMemorySnapshotRecord previousSnapshot = threadMemorySnapshotRepository.findByThread(userId, threadId).orElse(null);
        int fromIndex = Math.max(0, allMessages.size() - windowSize);
        List<MessageRecord> recentMessages = List.copyOf(allMessages.subList(fromIndex, allMessages.size()));
        String recentEndMessageId = recentMessages.isEmpty() ? null : recentMessages.get(recentMessages.size() - 1).messageId();
        ProjectionResult projection = projectSummary(userId, allMessages, recentMessages, previousSnapshot);
        ThreadMemorySnapshotRecord snapshot = threadMemorySnapshotRepository.save(userId, new ThreadMemorySnapshotRecord(
                threadId,
                userId,
                projection.summary(),
                projection.lastCompactedMessageId(),
                projection.pendingHistoricalMessages(),
                recentEndMessageId,
                windowSize,
                activeDraftId,
                activeTaskId,
                taskStage,
                previousSnapshot == null ? List.of() : previousSnapshot.activeSkillIds(),
                Instant.now()
        ));
        ThreadMemoryView memoryView = new ThreadMemoryView(
                threadId,
                displaySummary(snapshot.summary(), allMessages.isEmpty()),
                recentMessages,
                snapshot.pendingHistoricalMessages(),
                snapshot.activeDraftId(),
                snapshot.activeTaskId(),
                snapshot.taskStage()
        );
        return memoryView;
    }

    private String currentActiveDraftId(String userId, String threadId) {
        ResearchDraftRecord draft = researchDraftRepository.findActiveDraft(userId, threadId).orElse(null);
        if (draft == null || draft.status() == ResearchDraftStatus.STARTED) {
            return null;
        }
        return draft.draftId();
    }

    private String currentActiveTaskId(String userId, String threadId) {
        TaskRecord task = activeTask(userId, threadId);
        return task == null ? null : task.taskId();
    }

    private String currentTaskStage(String userId, String threadId) {
        TaskRecord task = activeTask(userId, threadId);
        return task == null ? null : task.stage();
    }

    private TaskRecord activeTask(String userId, String threadId) {
        return taskRepository.listTasks(userId, threadId).stream()
                .filter(task -> task.status() == TaskStatus.QUEUED || task.status() == TaskStatus.RUNNING)
                .max(Comparator.comparing(TaskRecord::updatedAt))
                .orElse(null);
    }

    private String normalizeSummary(String summary) {
        return summary == null ? "" : summary.trim();
    }

    private ProjectionResult projectSummary(String userId,
                                            List<MessageRecord> allMessages,
                                            List<MessageRecord> recentMessages,
                                            ThreadMemorySnapshotRecord previousSnapshot) {
        if (allMessages.isEmpty()) {
            return new ProjectionResult("", List.of(), null);
        }

        List<MessageRecord> historicalMessages = historicalMessages(allMessages);
        if (historicalMessages.isEmpty()) {
            return new ProjectionResult("", List.of(), null);
        }

        String existingSummary = previousSnapshot == null ? "" : normalizeSummary(previousSnapshot.summary());
        String lastCompactedMessageId = previousSnapshot == null ? null : previousSnapshot.lastCompactedMessageId();
        List<MessageRecord> existingPendingMessages = previousSnapshot == null
                ? List.of()
                : sanitizePendingMessages(previousSnapshot.pendingHistoricalMessages(), historicalMessages, lastCompactedMessageId);
        List<MessageRecord> newlyHistoricalMessages = newlyHistoricalMessages(allMessages, historicalMessages, existingPendingMessages, lastCompactedMessageId);
        List<MessageRecord> pendingHistoricalMessages = mergePendingMessages(existingPendingMessages, newlyHistoricalMessages);

        if (pendingHistoricalMessages.isEmpty()) {
            return new ProjectionResult(existingSummary, List.of(), lastCompactedMessageId);
        }

        if (!shouldCompress(existingSummary, pendingHistoricalMessages)) {
            return new ProjectionResult(existingSummary, pendingHistoricalMessages, lastCompactedMessageId);
        }

        String nextSummary = normalizeSummary(conversationSummaryCompressor.extendSummary(
                userId,
                existingSummary,
                pendingHistoricalMessages
        ));
        String nextLastCompactedMessageId = pendingHistoricalMessages.get(pendingHistoricalMessages.size() - 1).messageId();
        return new ProjectionResult(nextSummary, List.of(), nextLastCompactedMessageId);
    }

    private List<MessageRecord> historicalMessages(List<MessageRecord> allMessages) {
        int toIndex = Math.max(0, allMessages.size() - windowSize);
        return List.copyOf(allMessages.subList(0, toIndex));
    }

    private List<MessageRecord> sanitizePendingMessages(List<MessageRecord> pendingHistoricalMessages,
                                                        List<MessageRecord> historicalMessages,
                                                        String lastCompactedMessageId) {
        if (pendingHistoricalMessages == null || pendingHistoricalMessages.isEmpty()) {
            return List.of();
        }
        Set<String> historicalIds = historicalMessages.stream()
                .map(MessageRecord::messageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<MessageRecord> sanitized = pendingHistoricalMessages.stream()
                .filter(message -> message.messageId() != null)
                .filter(message -> historicalIds.contains(message.messageId()))
                .filter(message -> !Objects.equals(message.messageId(), lastCompactedMessageId))
                .toList();
        return List.copyOf(sanitized);
    }

    private List<MessageRecord> newlyHistoricalMessages(List<MessageRecord> allMessages,
                                                        List<MessageRecord> historicalMessages,
                                                        List<MessageRecord> existingPendingMessages,
                                                        String lastCompactedMessageId) {
        int historicalEndExclusive = historicalMessages.size();
        int compactedBoundaryIndex = indexOfMessage(allMessages, lastCompactedMessageId);
        Set<String> existingPendingIds = existingPendingMessages.stream()
                .map(MessageRecord::messageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<MessageRecord> promoted = new ArrayList<>();
        for (int index = compactedBoundaryIndex + 1; index < historicalEndExclusive; index++) {
            MessageRecord message = allMessages.get(index);
            if (message.messageId() == null || existingPendingIds.contains(message.messageId())) {
                continue;
            }
            promoted.add(message);
        }
        return List.copyOf(promoted);
    }

    private List<MessageRecord> mergePendingMessages(List<MessageRecord> existingPendingMessages,
                                                     List<MessageRecord> newlyHistoricalMessages) {
        if (existingPendingMessages.isEmpty()) {
            return List.copyOf(newlyHistoricalMessages);
        }
        if (newlyHistoricalMessages.isEmpty()) {
            return List.copyOf(existingPendingMessages);
        }
        List<MessageRecord> merged = new ArrayList<>(existingPendingMessages.size() + newlyHistoricalMessages.size());
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (MessageRecord message : existingPendingMessages) {
            if (message.messageId() != null && seen.add(message.messageId())) {
                merged.add(message);
            }
        }
        for (MessageRecord message : newlyHistoricalMessages) {
            if (message.messageId() != null && seen.add(message.messageId())) {
                merged.add(message);
            }
        }
        return List.copyOf(merged);
    }

    private boolean shouldCompress(String existingSummary, List<MessageRecord> pendingHistoricalMessages) {
        if (pendingHistoricalMessages == null || pendingHistoricalMessages.isEmpty()) {
            return false;
        }
        if (normalizeSummary(existingSummary).isBlank()) {
            return true;
        }
        if (pendingHistoricalMessages.size() >= maxPendingMessagesPerChunk()) {
            return true;
        }
        return estimatedChars(pendingHistoricalMessages) >= maxPendingCharsPerChunk();
    }

    private int maxPendingMessagesPerChunk() {
        if (conversationSummaryCompressor instanceof LlmConversationSummaryCompressor llmConversationSummaryCompressor) {
            return llmConversationSummaryCompressor.maxMessagesPerChunk();
        }
        return 12;
    }

    private int maxPendingCharsPerChunk() {
        if (conversationSummaryCompressor instanceof LlmConversationSummaryCompressor llmConversationSummaryCompressor) {
            return llmConversationSummaryCompressor.maxCharsPerChunk();
        }
        return 6000;
    }

    private int estimatedChars(List<MessageRecord> messages) {
        return messages.stream()
                .mapToInt(message -> {
                    String content = message.content() == null ? "" : message.content().trim().replaceAll("\\s+", " ");
                    return message.role().name().length() + Math.min(content.length(), 500) + 4;
                })
                .sum();
    }

    private int indexOfMessage(List<MessageRecord> messages, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return -1;
        }
        for (int index = 0; index < messages.size(); index++) {
            MessageRecord message = messages.get(index);
            if (messageId.equals(message.messageId())) {
                return index;
            }
        }
        return -1;
    }

    private String displaySummary(String summary, boolean noMessages) {
        if (noMessages) {
            return "No conversation memory yet.";
        }
        return normalizeSummary(summary);
    }

    private record ProjectionResult(
            String summary,
            List<MessageRecord> pendingHistoricalMessages,
            String lastCompactedMessageId
    ) {
    }
}
