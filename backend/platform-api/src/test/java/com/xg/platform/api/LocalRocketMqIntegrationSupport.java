package com.xg.platform.api;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.topic.TopicValidator;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class LocalRocketMqIntegrationSupport {

    private static final Set<String> ENSURED_TOPICS = ConcurrentHashMap.newKeySet();

    private LocalRocketMqIntegrationSupport() {
    }

    static String nameServer() {
        return required("platform.test.rocketmq.nameserver", "PLATFORM_TEST_ROCKETMQ_NAMESERVER");
    }

    static void ensureTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (!ENSURED_TOPICS.add(topic)) {
            return;
        }

        DefaultMQProducer producer = new DefaultMQProducer("platform-topic-bootstrap-" + UUID.randomUUID());
        producer.setNamesrvAddr(nameServer());
        producer.setVipChannelEnabled(false);

        try {
            producer.start();
            producer.createTopic(TopicValidator.AUTO_CREATE_TOPIC_KEY_TOPIC, topic, 4, Collections.emptyMap());
        } catch (MQClientException exception) {
            throw new IllegalStateException("Failed to pre-create RocketMQ topic " + topic, exception);
        } finally {
            producer.shutdown();
        }
    }

    private static String required(String propertyName, String envName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(envName);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing RocketMQ test setting. Provide -D" + propertyName + "=... or env " + envName
            );
        }
        return value;
    }
}
