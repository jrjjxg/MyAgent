package com.xg.platform.api.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class RocketMqEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "platformRocketMqBridge";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String asyncMode = environment.getProperty("platform.async.mode", "local");
        if (!"rocketmq".equalsIgnoreCase(asyncMode)) {
            return;
        }

        Map<String, Object> bridgedProperties = new LinkedHashMap<>();
        bridgeIfAbsent(
                environment,
                bridgedProperties,
                "rocketmq.name-server",
                environment.getProperty("platform.async.rocketmq.name-server")
        );
        bridgeIfAbsent(
                environment,
                bridgedProperties,
                "rocketmq.producer.group",
                environment.getProperty("platform.async.rocketmq.producer-group", "platform-producer")
        );

        if (!bridgedProperties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, bridgedProperties));
        }
    }

    private void bridgeIfAbsent(ConfigurableEnvironment environment,
                                Map<String, Object> bridgedProperties,
                                String targetKey,
                                String value) {
        if (environment.getProperty(targetKey) != null || value == null || value.isBlank()) {
            return;
        }
        bridgedProperties.put(targetKey, value);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
