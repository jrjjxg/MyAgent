package com.xg.platform.conversation.domain;

public record ConversationRouteDecision(
        ConversationRouteKind routeKind,
        String workflow,
        boolean toolsEnabled
) {
}
