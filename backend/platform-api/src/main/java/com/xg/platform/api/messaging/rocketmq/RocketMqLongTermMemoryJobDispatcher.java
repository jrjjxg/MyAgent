package com.xg.platform.api.messaging.rocketmq;

import com.xg.platform.runtime.LongTermMemoryExtractionRequest;
import com.xg.platform.runtime.LongTermMemoryJobDispatcher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

public class RocketMqLongTermMemoryJobDispatcher implements LongTermMemoryJobDispatcher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public RocketMqLongTermMemoryJobDispatcher(RocketMQTemplate rocketMQTemplate, String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Override
    public void dispatch(LongTermMemoryExtractionRequest request) {
        rocketMQTemplate.convertAndSend(topic, request);
    }
}
