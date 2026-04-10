package com.xg.platform.contracts.research;

import java.io.Serializable;
import java.util.List;

public record ApprovedResearchPlan(
        String draftId,
        Integer revision,
        String title,
        String brief,
        String objective,
        String scope,
        String outputFormat,
        List<String> constraints,
        String planSummary,
        List<ResearchPlanStep> planSteps
) implements Serializable {
    public ApprovedResearchPlan {
        revision = revision == null ? 0 : revision;
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        planSummary = planSummary == null ? "" : planSummary;
        planSteps = planSteps == null ? List.of() : List.copyOf(planSteps);
    }
}
