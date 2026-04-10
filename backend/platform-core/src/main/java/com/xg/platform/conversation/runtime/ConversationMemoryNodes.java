package com.xg.platform.conversation.runtime;

import java.util.Map;

final class ConversationMemoryNodes {

    private final ConversationRuntimeSupport support;

    ConversationMemoryNodes(ConversationRuntimeSupport support) {
        this.support = support;
    }

    Map<String, Object> loadShortTermMemory(InteractionState state) {
        return support.loadShortTermMemory(state);
    }

    Map<String, Object> loadLongTermMemory(InteractionState state) {
        return support.loadLongTermMemory(state);
    }

    Map<String, Object> loadDraftContext(InteractionState state) {
        return support.loadDraftContext(state);
    }
}
