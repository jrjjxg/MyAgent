package com.xg.platform.contracts.memory;

import com.xg.platform.contracts.conversation.MessageRecord;

import java.io.Serializable;
import java.util.List;

public record CachedThreadMemoryRecord(
        String threadId,
        String summary,
        List<MessageRecord> recentMessages,
        List<MessageRecord> pendingHistoricalMessages,
        String activeDraftId,
        String activeTaskId,
        String taskStage,
        String lastCompactedMessageId,
        String recentEndMessageId,
        int recentWindowSize
) implements Serializable {

    public CachedThreadMemoryRecord {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        pendingHistoricalMessages = pendingHistoricalMessages == null ? List.of() : List.copyOf(pendingHistoricalMessages);
    }

    public ThreadMemoryView toView() {
        return new ThreadMemoryView(
                threadId,
                summary,
                recentMessages,
                pendingHistoricalMessages,
                activeDraftId,
                activeTaskId,
                taskStage
        );
    }
}
