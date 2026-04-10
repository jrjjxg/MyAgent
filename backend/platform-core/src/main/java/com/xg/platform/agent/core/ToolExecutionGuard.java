package com.xg.platform.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.xg.platform.tooling.domain.ToolExecutionResult;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ToolExecutionGuard {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(new ToolExecutionThreadFactory());

    private ToolExecutionGuard() {
    }

    public static ToolExecutionResult execute(String toolName,
                                              long timeoutMs,
                                              Supplier<ToolExecutionResult> execution) {
        Objects.requireNonNull(execution, "execution");
        long effectiveTimeoutMs = Math.max(1L, timeoutMs);
        Future<ToolExecutionResult> future = EXECUTOR.submit(execution::get);
        try {
            ToolExecutionResult result = future.get(effectiveTimeoutMs, TimeUnit.MILLISECONDS);
            if (result == null) {
                throw new IllegalStateException(describe(toolName, "returned no result"));
            }
            if (result.error()) {
                throw new IllegalStateException(resolveFailureMessage(toolName, result));
            }
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IllegalStateException(
                    describe(toolName, "timed out after " + effectiveTimeoutMs + " ms"),
                    exception
            );
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(describe(toolName, "execution was interrupted"), exception);
        } catch (ExecutionException exception) {
            future.cancel(true);
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(
                    describe(toolName, "execution failed"),
                    cause == null ? exception : cause
            );
        }
    }

    public static String resolveFailureMessage(String toolName, ToolExecutionResult result) {
        if (result == null) {
            return describe(toolName, "execution failed");
        }
        if (hasText(result.message())) {
            return result.message().trim();
        }
        JsonNode output = result.output();
        if (output != null && hasText(output.path("error").asText(null))) {
            return output.path("error").asText().trim();
        }
        return describe(hasText(result.toolName()) ? result.toolName() : toolName, "failed");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String describe(String toolName, String detail) {
        if (!hasText(toolName)) {
            return "Tool " + detail;
        }
        return toolName.trim() + " " + detail;
    }

    private static final class ToolExecutionThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "tool-execution-guard-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
