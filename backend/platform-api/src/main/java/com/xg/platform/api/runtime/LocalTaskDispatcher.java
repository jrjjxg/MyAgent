package com.xg.platform.api.runtime;

import com.xg.platform.runtime.TaskDispatchRequest;
import com.xg.platform.runtime.TaskDispatcher;
import com.xg.platform.runtime.TaskProcessor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.ExecutorService;

public class LocalTaskDispatcher implements TaskDispatcher {

    private static final String RETRYABLE_TASK_EXCEPTION = "com.xg.platform.runtime.RetryableTaskException";

    private final ExecutorService executorService;
    private final ObjectProvider<TaskProcessor> taskProcessorProvider;

    public LocalTaskDispatcher(ExecutorService executorService, ObjectProvider<TaskProcessor> taskProcessorProvider) {
        this.executorService = executorService;
        this.taskProcessorProvider = taskProcessorProvider;
    }

    @Override
    public void dispatch(TaskDispatchRequest request) {
        executorService.execute(() -> process(request));
    }

    private void process(TaskDispatchRequest request) {
        try {
            taskProcessorProvider.getObject().process(request);
        } catch (RuntimeException exception) {
            if (isRetryableTaskFailure(exception)) {
                executorService.execute(() -> process(request));
                return;
            }
            throw exception;
        }
    }

    private boolean isRetryableTaskFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (RETRYABLE_TASK_EXCEPTION.equals(current.getClass().getName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
