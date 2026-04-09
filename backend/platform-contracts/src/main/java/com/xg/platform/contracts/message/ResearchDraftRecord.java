package com.xg.platform.contracts.message;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ResearchDraftRecord(
        String draftId,
        String threadId,
        ResearchDraftStatus status,
        String title,
        String brief,
        String objective,
        String scope,
        String outputFormat,
        List<String> constraints,
        List<String> questions,
        Integer revision,
        String planSummary,
        List<ResearchPlanStep> planSteps,
        boolean ready,
        String lastUserMessageId,
        String lastAssistantMessageId,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
    public ResearchDraftRecord {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        questions = questions == null ? List.of() : List.copyOf(questions);
        revision = revision == null ? 0 : revision;
        planSummary = planSummary == null ? "" : planSummary;
        planSteps = planSteps == null ? List.of() : List.copyOf(planSteps);
    }
}
