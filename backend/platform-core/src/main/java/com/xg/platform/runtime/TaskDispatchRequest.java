package com.xg.platform.runtime;

import com.xg.platform.contracts.task.TaskKind;

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

    public TaskDispatchRequest(String userId,
                               String threadId,
                               String taskId,
                               TaskKind taskKind,
                               String providerId,
                               String taskInput) {
        this(userId, threadId, taskId, taskKind, providerId, taskInput, null);
    }
}
