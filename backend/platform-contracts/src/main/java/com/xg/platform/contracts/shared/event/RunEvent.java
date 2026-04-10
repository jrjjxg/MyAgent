package com.xg.platform.contracts.shared.event;

import java.time.Instant;

public record RunEvent(
        String runId,
        String threadId,
        String eventType,
        Instant timestamp,
        Object payload
) {
}
