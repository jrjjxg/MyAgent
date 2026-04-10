package com.xg.platform.conversation.runtime;

import java.util.Map;

final class ConversationRoutingNodes {

    private final ConversationRuntimeSupport support;

    ConversationRoutingNodes(ConversationRuntimeSupport support) {
        this.support = support;
    }

    Map<String, Object> routeInteraction(InteractionState state) {
        return support.routeInteraction(state);
    }
}
