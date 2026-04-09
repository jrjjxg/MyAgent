package com.xg.platform.agent.core;

import java.io.Serializable;

public record ExecutionSource(
        String kind,
        String title,
        String domain,
        String url,
        boolean verified,
        boolean usedInAnswer
) implements Serializable {
}
