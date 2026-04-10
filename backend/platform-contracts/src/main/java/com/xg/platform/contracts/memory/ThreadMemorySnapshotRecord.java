package com.xg.platform.contracts.memory;

import com.xg.platform.contracts.conversation.MessageRecord;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ThreadMemorySnapshotRecord(
        String threadId,
        String userId,
        String summary,
        String lastCompactedMessageId,
        List<MessageRecord> pendingHistoricalMessages,
        String recentEndMessageId,
        int recentWindowSize,
        String activeDraftId,
        String activeTaskId,
        String taskStage,
        List<String> activeSkillIds,
        Instant updatedAt
) implements Serializable {

    public ThreadMemorySnapshotRecord {
        pendingHistoricalMessages = pendingHistoricalMessages == null ? List.of() : List.copyOf(pendingHistoricalMessages);
        activeSkillIds = activeSkillIds == null ? List.of() : List.copyOf(activeSkillIds);
    }

    public static ThreadMemorySnapshotRecord withoutActiveSkillIds(String threadId,
                                                                   String userId,
                                                                   String summary,
                                                                   String lastCompactedMessageId,
                                                                   List<MessageRecord> pendingHistoricalMessages,
                                                                   String recentEndMessageId,
                                                                   int recentWindowSize,
                                                                   String activeDraftId,
                                                                   String activeTaskId,
                                                                   String taskStage,
                                                                   Instant updatedAt) {
        return new ThreadMemorySnapshotRecord(
                threadId,
                userId,
                summary,
                lastCompactedMessageId,
                pendingHistoricalMessages,
                recentEndMessageId,
                recentWindowSize,
                activeDraftId,
                activeTaskId,
                taskStage,
                List.of(),
                updatedAt
        );
    }
}
