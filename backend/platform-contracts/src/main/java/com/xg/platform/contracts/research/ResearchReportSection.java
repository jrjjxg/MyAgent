package com.xg.platform.contracts.research;

import java.io.Serializable;

public record ResearchReportSection(
        String sectionId,
        String title,
        String summary
) implements Serializable {
}
