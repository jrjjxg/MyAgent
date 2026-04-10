package com.xg.platform.api.messaging.rocketmq;

import com.xg.platform.memory.port.MemoryEventPayload;
import com.xg.platform.memory.port.MemoryEventProcessor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "platform.async", name = "mode", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "${platform.async.rocketmq.memory-topic:platform_memory_events}",
        consumerGroup = "${platform.async.rocketmq.memory-consumer-group:platform-memory-consumer}"
)
public class RocketMqMemoryEventConsumer implements RocketMQListener<MemoryEventPayload> {

    private final MemoryEventProcessor memoryEventProcessor;

    public RocketMqMemoryEventConsumer(MemoryEventProcessor memoryEventProcessor) {
        this.memoryEventProcessor = memoryEventProcessor;
    }

    @Override
    public void onMessage(MemoryEventPayload payload) {
        memoryEventProcessor.process(payload);
    }
}
