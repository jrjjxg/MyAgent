package com.xg.platform.contracts.memory;

import com.xg.platform.contracts.message.MessageRecord;

import java.io.Serializable;
import java.util.List;

public record ThreadMemoryView(
        String threadId,
        String summary,
        List<MessageRecord> recentMessages,
        List<MessageRecord> pendingHistoricalMessages,
        String activeDraftId,
        String activeTaskId,
        String taskStage
) implements Serializable {

    public ThreadMemoryView {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        pendingHistoricalMessages = pendingHistoricalMessages == null ? List.of() : List.copyOf(pendingHistoricalMessages);
    }
}
