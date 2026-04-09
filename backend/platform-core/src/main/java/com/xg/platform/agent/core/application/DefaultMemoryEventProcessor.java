package com.xg.platform.agent.core.application;

import com.xg.platform.runtime.MemoryEventPayload;
import com.xg.platform.runtime.MemoryEventProcessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultMemoryEventProcessor implements MemoryEventProcessor, AutoCloseable {

    private static final Logger logger = Logger.getLogger(DefaultMemoryEventProcessor.class.getName());

    private final ShortTermMemoryProjectionService shortTermMemoryProjectionService;
    private final LongTermMemoryJobScheduler longTermMemoryJobScheduler;
    private final long projectorDebounceMs;
    private final ScheduledExecutorService projectionScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledProjections = new ConcurrentHashMap<>();

    public DefaultMemoryEventProcessor(ShortTermMemoryProjectionService shortTermMemoryProjectionService,
                                       LongTermMemoryJobScheduler longTermMemoryJobScheduler,
                                       long projectorDebounceMs) {
        this.shortTermMemoryProjectionService = shortTermMemoryProjectionService;
        this.longTermMemoryJobScheduler = longTermMemoryJobScheduler;
        this.projectorDebounceMs = Math.max(0L, projectorDebounceMs);
        this.projectionScheduler = Executors.newScheduledThreadPool(2);
    }

    @Override
    public void process(MemoryEventPayload payload) {
        if (payload == null || payload.userId() == null || payload.threadId() == null || payload.eventType() == null) {
            return;
        }
        switch (payload.eventType()) {
            case "message.completed" -> {
                scheduleProjection(payload.userId(), payload.threadId());
                longTermMemoryJobScheduler.schedule(payload.userId(), payload.threadId(), payload.messageId());
            }
            case "research.brief.updated", "task.created", "task.stage.changed", "task.completed", "task.failed", "task.cancelled" ->
                    scheduleProjection(payload.userId(), payload.threadId());
            default -> {
            }
        }
    }

    private void scheduleProjection(String userId, String threadId) {
        String key = userId + "::" + threadId;
        scheduledProjections.compute(key, (ignored, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            return projectionScheduler.schedule(() -> runProjection(key, userId, threadId), projectorDebounceMs, TimeUnit.MILLISECONDS);
        });
    }

    private void runProjection(String key, String userId, String threadId) {
        try {
            shortTermMemoryProjectionService.projectThreadMemory(userId, threadId);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to project thread memory for " + threadId, exception);
        } finally {
            scheduledProjections.remove(key);
        }
    }

    @Override
    public void close() {
        projectionScheduler.shutdown();
    }
}
