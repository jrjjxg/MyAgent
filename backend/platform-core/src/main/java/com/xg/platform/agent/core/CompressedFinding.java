package com.xg.platform.agent.core;

import java.io.Serializable;
import java.util.List;

public record CompressedFinding(
        String heading,
        String summary,
        String evidenceStrength,
        List<String> supportingSources
) implements Serializable {
}
