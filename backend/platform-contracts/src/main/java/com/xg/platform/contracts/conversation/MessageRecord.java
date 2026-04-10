package com.xg.platform.contracts.conversation;

import java.io.Serializable;
import java.time.Instant;

public record MessageRecord(
        String messageId,
        String threadId,
        MessageRole role,
        String content,
        InteractionMode interactionMode,
        String runId,
        String taskId,
        Instant createdAt
) implements Serializable {
}
