package com.xg.platform.agent.core;

import com.xg.platform.contracts.message.RunEventType;

public interface AgentOutputEmitter {

    void emitText(String delta);

    default void emitEvent(RunEventType eventType, Object payload) {
    }
}
