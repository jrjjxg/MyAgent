package com.xg.platform.research.application;

import com.xg.platform.research.runtime.ResearchTaskState;
import com.xg.platform.shared.runtime.async.TaskDispatchRequest;
import com.xg.platform.shared.runtime.graph.PlatformGraphRunner;

import java.util.Map;

public class ResearchTaskExecutionService {

    private final PlatformGraphRunner platformGraphRunner;

    public ResearchTaskExecutionService(PlatformGraphRunner platformGraphRunner) {
        this.platformGraphRunner = platformGraphRunner;
    }

    public void execute(TaskDispatchRequest request) {
        platformGraphRunner.invokeResearch(Map.of(
                        ResearchTaskState.USER_ID, request.userId(),
                        ResearchTaskState.THREAD_ID, request.threadId(),
                        ResearchTaskState.TASK_ID, request.taskId(),
                        ResearchTaskState.PROVIDER_ID, request.providerId(),
                        ResearchTaskState.TASK_INPUT, request.taskInput()
                ),
                request.taskId());
    }
}
