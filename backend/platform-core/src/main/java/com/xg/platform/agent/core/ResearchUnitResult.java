package com.xg.platform.agent.core;

import com.xg.platform.contracts.research.ResearchSourceRecord;

import java.io.Serializable;
import java.util.List;

public record ResearchUnitResult(
        String unitId,
        String title,
        String query,
        String notes,
        String localConclusion,
        List<String> sources,
        List<ResearchSourceRecord> sourceRecords
) implements Serializable {

    public static ResearchUnitResult withoutSourceRecords(String unitId,
                                                          String title,
                                                          String query,
                                                          String notes,
                                                          String localConclusion,
                                                          List<String> sources) {
        return new ResearchUnitResult(unitId, title, query, notes, localConclusion, sources, List.of());
    }
}
