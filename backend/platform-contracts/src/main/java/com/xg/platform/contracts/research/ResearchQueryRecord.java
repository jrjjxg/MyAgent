package com.xg.platform.contracts.research;

import java.io.Serializable;

public record ResearchQueryRecord(
        String queryId,
        int iterationNo,
        String phase,
        String query,
        String intent,
        String quality,
        int candidateCount,
        int verifiedCount
) implements Serializable {
}
