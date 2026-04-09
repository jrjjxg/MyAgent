package com.xg.platform.contracts.research;

import java.io.Serializable;
import java.util.List;

public record ResearchReportView(
        String markdown,
        List<ResearchReportBlock> blocks
) implements Serializable {
}
