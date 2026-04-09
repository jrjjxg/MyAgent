package com.xg.platform.contracts.research;

import java.io.Serializable;
import java.util.List;

public record ResearchIterationRecord(
        int iterationNo,
        String phase,
        String summary,
        List<String> confirmedFindings,
        List<String> openQuestions,
        List<String> nextSearchIntent,
        List<String> queryIds,
        List<String> sourceIds
) implements Serializable {
}
