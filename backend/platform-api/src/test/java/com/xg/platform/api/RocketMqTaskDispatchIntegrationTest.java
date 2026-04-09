package com.xg.platform.api;

import com.xg.platform.api.config.AsyncConfig;
import com.xg.platform.api.config.PlatformPropertiesConfig;
import com.xg.platform.api.messaging.rocketmq.RocketMqTaskConsumer;
import com.xg.platform.api.messaging.rocketmq.RocketMqTaskDispatcher;
import com.xg.platform.contracts.task.TaskKind;
import com.xg.platform.runtime.LongTermMemoryJobProcessor;
import com.xg.platform.runtime.MemoryEventProcessor;
import com.xg.platform.runtime.TaskDispatchRequest;
import com.xg.platform.runtime.TaskDispatcher;
import com.xg.platform.runtime.TaskProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RocketMqTaskDispatchIntegrationTest.TestApplication.class, webEnvironment = WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "platform.test.rocketmq.enabled", matches = "true")
class RocketMqTaskDispatchIntegrationTest {

    private static final String TEST_ID = UUID.randomUUID().toString().replace("-", "");
    private static final String TEST_TOPIC = "platform_tasks_dispatch_test_" + TEST_ID;
    private static final LinkedBlockingQueue<TaskDispatchRequest> DISPATCHED_REQUESTS = new LinkedBlockingQueue<>();

    @Autowired
    private TaskDispatcher taskDispatcher;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        LocalRocketMqIntegrationSupport.ensureTopic(TEST_TOPIC);
        registry.add("platform.async.mode", () -> "rocketmq");
        registry.add("platform.async.rocketmq.name-server", LocalRocketMqIntegrationSupport::nameServer);
        registry.add("platform.async.rocketmq.producer-group", () -> "platform-test-producer-" + TEST_ID);
        registry.add("platform.async.rocketmq.task-topic", () -> TEST_TOPIC);
        registry.add("platform.async.rocketmq.task-consumer-group", () -> "platform-task-consumer-" + TEST_ID);
        registry.add("rocketmq.name-server", LocalRocketMqIntegrationSupport::nameServer);
        registry.add("rocketmq.producer.group", () -> "platform-test-producer-" + TEST_ID);
    }

    @AfterEach
    void clearQueue() {
        DISPATCHED_REQUESTS.clear();
    }

    @Test
    void dispatchesTasksThroughRocketMq() throws Exception {
        assertThat(taskDispatcher).isInstanceOf(RocketMqTaskDispatcher.class);

        TaskDispatchRequest request = new TaskDispatchRequest(
                "user-rocketmq",
                "thread-rocketmq",
                "task-rocketmq",
                TaskKind.RESEARCH,
                "gemini",
                "{\"message\":\"hello rocketmq\"}"
        );

        taskDispatcher.dispatch(request);

        TaskDispatchRequest received = DISPATCHED_REQUESTS.poll(15, TimeUnit.SECONDS);
        assertThat(received).isEqualTo(request);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({PlatformPropertiesConfig.class, AsyncConfig.class, RocketMqTaskConsumer.class})
    static class TestApplication {

        @Bean
        TaskProcessor taskProcessor() {
            return request -> DISPATCHED_REQUESTS.offer(request);
        }

        @Bean
        MemoryEventProcessor memoryEventProcessor() {
            return payload -> {
            };
        }

        @Bean
        LongTermMemoryJobProcessor longTermMemoryJobProcessor() {
            return request -> {
            };
        }
    }
}
