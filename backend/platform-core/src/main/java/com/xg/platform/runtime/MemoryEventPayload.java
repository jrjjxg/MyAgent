package com.xg.platform.runtime;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public record MemoryEventPayload(
        String eventType,
        String userId,
        String threadId,
        String taskId,
        String messageId,
        Instant createdAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
