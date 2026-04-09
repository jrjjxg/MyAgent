package com.xg.platform.runtime;

import java.io.Serial;
import java.io.Serializable;

public record LongTermMemoryExtractionRequest(
        String jobId,
        String userId,
        String threadId,
        String messageId,
        String extractorVersion
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
