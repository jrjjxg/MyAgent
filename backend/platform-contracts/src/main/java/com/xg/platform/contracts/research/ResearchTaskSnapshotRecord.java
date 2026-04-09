package com.xg.platform.contracts.research;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ResearchTaskSnapshotRecord(
        String taskId,
        String threadId,
        String phase,
        Integer iterationNo,
        List<ResearchReportSection> plan,
        List<ResearchIterationRecord> iterations,
        List<ResearchFindingRecord> findings,
        List<ResearchSourceRecord> sources,
        List<ReportCitation> citations,
        String summary,
        boolean converged,
        Instant updatedAt
) implements Serializable {

    public ResearchTaskSnapshotRecord {
        plan = plan == null ? List.of() : List.copyOf(plan);
        iterations = iterations == null ? List.of() : List.copyOf(iterations);
        findings = findings == null ? List.of() : List.copyOf(findings);
        sources = sources == null ? List.of() : List.copyOf(sources);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
