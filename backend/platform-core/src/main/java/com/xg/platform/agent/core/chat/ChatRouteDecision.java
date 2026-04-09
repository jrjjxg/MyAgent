package com.xg.platform.agent.core.chat;

public record ChatRouteDecision(
        ChatRouteKind routeKind,
        String workflow,
        boolean toolsEnabled
) {
}
