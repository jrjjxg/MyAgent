package com.xg.platform.api.messaging.rocketmq;

import com.xg.platform.runtime.TaskDispatchRequest;
import com.xg.platform.runtime.TaskDispatcher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

public class RocketMqTaskDispatcher implements TaskDispatcher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String taskTopic;

    public RocketMqTaskDispatcher(RocketMQTemplate rocketMQTemplate, String taskTopic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.taskTopic = taskTopic;
    }

    @Override
    public void dispatch(TaskDispatchRequest request) {
        rocketMQTemplate.convertAndSend(taskTopic, request);
    }
}
