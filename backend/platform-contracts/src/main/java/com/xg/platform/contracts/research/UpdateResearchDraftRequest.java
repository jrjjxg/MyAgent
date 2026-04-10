package com.xg.platform.contracts.research;

import java.util.List;

public record UpdateResearchDraftRequest(
        Integer revision,
        String title,
        String brief,
        String objective,
        String scope,
        String outputFormat,
        List<String> constraints,
        List<String> questions,
        String planSummary,
        List<ResearchPlanStep> planSteps
) {
}
