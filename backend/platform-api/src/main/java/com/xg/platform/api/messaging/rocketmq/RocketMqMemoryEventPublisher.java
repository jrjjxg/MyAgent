package com.xg.platform.api.messaging.rocketmq;

import com.xg.platform.runtime.MemoryEventPayload;
import com.xg.platform.runtime.MemoryEventPublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

public class RocketMqMemoryEventPublisher implements MemoryEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public RocketMqMemoryEventPublisher(RocketMQTemplate rocketMQTemplate, String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(MemoryEventPayload payload) {
        rocketMQTemplate.convertAndSend(topic, payload);
    }
}
