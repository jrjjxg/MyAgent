package com.xg.platform.api.messaging.rocketmq;

import com.xg.platform.memory.port.LongTermMemoryExtractionRequest;
import com.xg.platform.memory.port.LongTermMemoryJobProcessor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "platform.async", name = "mode", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "${platform.async.rocketmq.long-term-memory-topic:platform_memory_long_term_jobs}",
        consumerGroup = "${platform.async.rocketmq.long-term-memory-consumer-group:platform-long-term-memory-consumer}"
)
public class RocketMqLongTermMemoryJobConsumer implements RocketMQListener<LongTermMemoryExtractionRequest> {

    private final LongTermMemoryJobProcessor longTermMemoryJobProcessor;

    public RocketMqLongTermMemoryJobConsumer(LongTermMemoryJobProcessor longTermMemoryJobProcessor) {
        this.longTermMemoryJobProcessor = longTermMemoryJobProcessor;
    }

    @Override
    public void onMessage(LongTermMemoryExtractionRequest request) {
        longTermMemoryJobProcessor.process(request);
    }
}
