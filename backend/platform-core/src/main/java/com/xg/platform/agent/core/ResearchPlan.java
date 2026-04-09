package com.xg.platform.agent.core;

import java.io.Serializable;
import java.util.List;

public record ResearchPlan(
        String summary,
        List<ResearchUnit> units
) implements Serializable {
}
