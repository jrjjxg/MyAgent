package com.xg.platform.contracts.research;

import java.io.Serializable;
import java.util.List;

public record ResearchFindingRecord(
        String findingId,
        String title,
        String summary,
        String confidence,
        String scopeLimit,
        List<String> supportingSourceIds,
        boolean usedInReport,
        String reportSectionId
) implements Serializable {
}
