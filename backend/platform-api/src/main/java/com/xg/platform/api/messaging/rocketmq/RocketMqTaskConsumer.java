package com.xg.platform.api.messaging.rocketmq;

import com.xg.platform.runtime.TaskDispatchRequest;
import com.xg.platform.runtime.TaskProcessor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "platform.async", name = "mode", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "${platform.async.rocketmq.task-topic:platform_tasks_dispatch}",
        consumerGroup = "${platform.async.rocketmq.task-consumer-group:platform-task-consumer}"
)
public class RocketMqTaskConsumer implements RocketMQListener<TaskDispatchRequest> {

    private final TaskProcessor taskProcessor;

    public RocketMqTaskConsumer(TaskProcessor taskProcessor) {
        this.taskProcessor = taskProcessor;
    }

    @Override
    public void onMessage(TaskDispatchRequest request) {
        taskProcessor.process(request);
    }
}
