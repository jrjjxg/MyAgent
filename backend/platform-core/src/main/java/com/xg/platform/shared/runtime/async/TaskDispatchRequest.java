package com.xg.platform.shared.runtime.async;

import com.xg.platform.contracts.shared.task.TaskKind;

import java.io.Serial;
import java.io.Serializable;

public record TaskDispatchRequest(
        String userId,
        String threadId,
        String taskId,
        TaskKind taskKind,
        String providerId,
        String taskInput,
        String workspaceId
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static TaskDispatchRequest of(String userId,
                                         String threadId,
                                         String taskId,
                                         TaskKind taskKind,
                                         String providerId,
                                         String taskInput) {
        return new TaskDispatchRequest(userId, threadId, taskId, taskKind, providerId, taskInput, null);
    }
}
