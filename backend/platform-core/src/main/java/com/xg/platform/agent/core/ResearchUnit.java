package com.xg.platform.agent.core;

import java.io.Serializable;

public record ResearchUnit(
        String unitId,
        String title,
        String objective,
        String query,
        boolean useDocuments,
        boolean useWeb,
        String outputFocus
) implements Serializable {
}
