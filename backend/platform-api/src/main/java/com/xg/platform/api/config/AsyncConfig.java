package com.xg.platform.api.config;

import com.xg.platform.api.messaging.rocketmq.RocketMqLongTermMemoryJobDispatcher;
import com.xg.platform.api.messaging.rocketmq.RocketMqMemoryEventPublisher;
import com.xg.platform.api.messaging.rocketmq.RocketMqTaskDispatcher;
import com.xg.platform.api.runtime.LocalLongTermMemoryJobDispatcher;
import com.xg.platform.api.runtime.LocalMemoryEventPublisher;
import com.xg.platform.api.runtime.LocalTaskDispatcher;
import com.xg.platform.runtime.LongTermMemoryJobDispatcher;
import com.xg.platform.runtime.LongTermMemoryJobProcessor;
import com.xg.platform.runtime.MemoryEventProcessor;
import com.xg.platform.runtime.MemoryEventPublisher;
import com.xg.platform.runtime.TaskDispatcher;
import com.xg.platform.runtime.TaskProcessor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class AsyncConfig {

    @Bean
    MemoryEventPublisher memoryEventPublisher(PlatformProperties properties,
                                              ExecutorService memoryProjectionExecutor,
                                              MemoryEventProcessor memoryEventProcessor,
                                              ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        if ("rocketmq".equalsIgnoreCase(properties.getAsync().getMode())
                && rocketMQTemplateProvider.getIfAvailable() != null) {
            return new RocketMqMemoryEventPublisher(
                    rocketMQTemplateProvider.getObject(),
                    properties.getAsync().getRocketmq().getMemoryTopic()
            );
        }
        return new LocalMemoryEventPublisher(memoryProjectionExecutor, memoryEventProcessor);
    }

    @Bean
    TaskDispatcher taskDispatcher(PlatformProperties properties,
                                  ExecutorService agentExecutionExecutor,
                                  ObjectProvider<TaskProcessor> taskProcessorProvider,
                                  ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        if ("rocketmq".equalsIgnoreCase(properties.getAsync().getMode())
                && rocketMQTemplateProvider.getIfAvailable() != null) {
            return new RocketMqTaskDispatcher(
                    rocketMQTemplateProvider.getObject(),
                    properties.getAsync().getRocketmq().getTaskTopic()
            );
        }
        return new LocalTaskDispatcher(agentExecutionExecutor, taskProcessorProvider);
    }

    @Bean
    LongTermMemoryJobDispatcher longTermMemoryJobDispatcher(PlatformProperties properties,
                                                            ExecutorService longTermMemoryJobExecutor,
                                                            ObjectProvider<LongTermMemoryJobProcessor> longTermMemoryJobProcessorProvider,
                                                            ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        if ("rocketmq".equalsIgnoreCase(properties.getAsync().getMode())
                && rocketMQTemplateProvider.getIfAvailable() != null) {
            return new RocketMqLongTermMemoryJobDispatcher(
                    rocketMQTemplateProvider.getObject(),
                    properties.getAsync().getRocketmq().getLongTermMemoryTopic()
            );
        }
        return new LocalLongTermMemoryJobDispatcher(longTermMemoryJobExecutor, longTermMemoryJobProcessorProvider);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService memoryProjectionExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService agentExecutionExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService longTermMemoryJobExecutor() {
        return Executors.newCachedThreadPool();
    }
}
