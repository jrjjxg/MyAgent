package com.xg.platform.conversation.runtime;

import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.memory.port.MemoryEventPayload;
import com.xg.platform.memory.port.MemoryEventPublisher;
import com.xg.platform.shared.port.RunEventRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ConversationEventSupport {

    private final RunEventRepository runEventRepository;
    private final MemoryEventPublisher memoryEventPublisher;

    ConversationEventSupport(RunEventRepository runEventRepository,
                             MemoryEventPublisher memoryEventPublisher) {
        this.runEventRepository = runEventRepository;
        this.memoryEventPublisher = memoryEventPublisher;
    }

    AgentOutputEmitter stepEventEmitter(String userId,
                                        String threadId,
                                        String runId,
                                        Consumer<RunEvent> runEventConsumer) {
        return new StepEventEmitter(userId, threadId, runId, runEventConsumer);
    }

    void publishEvent(String userId,
                      String threadId,
                      String runId,
                      RunEventType runEventType,
                      Object payload,
                      Consumer<RunEvent> runEventConsumer) {
        RunEvent event = new RunEvent(runId, threadId, runEventType.value(), Instant.now(), payload);
        runEventRepository.appendEvent(userId, threadId, event);
        runEventConsumer.accept(event);
    }

    void publishMemoryEvent(RunEventType eventType,
                            String userId,
                            String threadId,
                            String taskId,
                            String messageId) {
        memoryEventPublisher.publish(new MemoryEventPayload(
                eventType.value(),
                userId,
                threadId,
                taskId,
                messageId,
                Instant.now()
        ));
    }

    Iterable<String> split(String response, int chunkSize) {
        List<String> segments = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return segments;
        }
        for (int index = 0; index < response.length(); index += chunkSize) {
            int end = Math.min(response.length(), index + chunkSize);
            segments.add(response.substring(index, end));
        }
        return segments;
    }

    String summarize(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private final class StepEventEmitter implements AgentOutputEmitter {

        private final String userId;
        private final String threadId;
        private final String runId;
        private final Consumer<RunEvent> runEventConsumer;

        private StepEventEmitter(String userId,
                                 String threadId,
                                 String runId,
                                 Consumer<RunEvent> runEventConsumer) {
            this.userId = userId;
            this.threadId = threadId;
            this.runId = runId;
            this.runEventConsumer = runEventConsumer;
        }

        @Override
        public void emitText(String delta) {
            // Single-step text is surfaced through explicit process events, not the final assistant channel.
        }

        @Override
        public void emitEvent(RunEventType eventType, Object payload) {
            publishEvent(userId, threadId, runId, eventType, payload, runEventConsumer);
        }
    }
}
