package com.xg.platform.contracts.research;

import java.io.Serializable;
import java.util.List;

public record ResearchReportBlock(
        String blockId,
        String paragraphId,
        String text,
        List<String> citationIds
) implements Serializable {
}
