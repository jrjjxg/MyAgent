package com.xg.platform.contracts.research;

import java.io.Serializable;

public record ResearchGapRecord(
        String gapId,
        int iterationNo,
        String topic,
        String reason,
        String strategy,
        boolean resolved
) implements Serializable {
}
