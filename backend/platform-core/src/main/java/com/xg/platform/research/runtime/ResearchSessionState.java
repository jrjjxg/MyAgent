package com.xg.platform.research.runtime;

import com.xg.platform.contracts.research.ResearchAgendaItem;
import com.xg.platform.contracts.research.ResearchFindingRecord;
import com.xg.platform.contracts.research.ResearchGapRecord;
import com.xg.platform.contracts.research.ResearchIterationRecord;
import com.xg.platform.contracts.research.ResearchQueryRecord;
import com.xg.platform.contracts.research.ResearchReportSection;
import com.xg.platform.contracts.research.ResearchSourceRecord;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ResearchSessionState(
        String researchBrief,
        List<ResearchAgendaItem> agenda,
        List<ResearchQueryRecord> queryHistory,
        List<ResearchSourceRecord> sourceLedger,
        List<ResearchFindingRecord> findingLedger,
        List<ResearchGapRecord> gapLedger,
        List<ResearchIterationRecord> iterationNotes,
        List<ResearchReportSection> reportPlan,
        List<String> pendingQueries,
        int iterationNo,
        String phase,
        boolean converged,
        String convergenceSummary,
        Instant startedAt,
        String stopReason,
        String convergenceReason,
        int maxIterations,
        long maxWallTimeMs
) implements Serializable {

    public ResearchSessionState {
        agenda = agenda == null ? List.of() : List.copyOf(agenda);
        queryHistory = queryHistory == null ? List.of() : List.copyOf(queryHistory);
        sourceLedger = sourceLedger == null ? List.of() : List.copyOf(sourceLedger);
        findingLedger = findingLedger == null ? List.of() : List.copyOf(findingLedger);
        gapLedger = gapLedger == null ? List.of() : List.copyOf(gapLedger);
        iterationNotes = iterationNotes == null ? List.of() : List.copyOf(iterationNotes);
        reportPlan = reportPlan == null ? List.of() : List.copyOf(reportPlan);
        pendingQueries = pendingQueries == null ? List.of() : List.copyOf(pendingQueries);
    }

    public static ResearchSessionState empty(String researchBrief) {
        return new ResearchSessionState(
                researchBrief,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0,
                "initialize_session",
                false,
                "",
                Instant.now(),
                "",
                "",
                8,
                600_000L
        );
    }
}
