package com.xg.platform.agent.core;

import com.xg.platform.contracts.shared.event.RunEventType;

public interface AgentOutputEmitter {

    void emitText(String delta);

    default void emitEvent(RunEventType eventType, Object payload) {
    }
}
