package com.xg.platform.contracts.message;

import java.io.Serializable;

public record ResearchPlanStep(
        String stepId,
        String title,
        String objective,
        String query,
        boolean useWeb,
        boolean useDocuments,
        String outputFocus
) implements Serializable {
}
