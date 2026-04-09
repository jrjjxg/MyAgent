package com.xg.platform.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqEnvironmentPostProcessorTest {

    private final RocketMqEnvironmentPostProcessor postProcessor = new RocketMqEnvironmentPostProcessor();

    @Test
    void bridgesStarterPropertiesWhenRocketMqModeIsEnabled() {
        ConfigurableEnvironment environment = new MockEnvironment()
                .withProperty("platform.async.mode", "rocketmq")
                .withProperty("platform.async.rocketmq.name-server", "127.0.0.1:9876")
                .withProperty("platform.async.rocketmq.producer-group", "platform-producer");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("rocketmq.name-server")).isEqualTo("127.0.0.1:9876");
        assertThat(environment.getProperty("rocketmq.producer.group")).isEqualTo("platform-producer");
    }

    @Test
    void doesNothingOutsideRocketMqMode() {
        ConfigurableEnvironment environment = new MockEnvironment()
                .withProperty("platform.async.mode", "local")
                .withProperty("platform.async.rocketmq.name-server", "127.0.0.1:9876");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("rocketmq.name-server")).isNull();
        assertThat(environment.getProperty("rocketmq.producer.group")).isNull();
    }

    @Test
    void preservesExplicitStarterProperties() {
        ConfigurableEnvironment environment = new MockEnvironment()
                .withProperty("platform.async.mode", "rocketmq")
                .withProperty("platform.async.rocketmq.name-server", "127.0.0.1:9876")
                .withProperty("rocketmq.name-server", "mq.example.internal:9876")
                .withProperty("rocketmq.producer.group", "custom-producer");

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("rocketmq.name-server")).isEqualTo("mq.example.internal:9876");
        assertThat(environment.getProperty("rocketmq.producer.group")).isEqualTo("custom-producer");
    }
}
