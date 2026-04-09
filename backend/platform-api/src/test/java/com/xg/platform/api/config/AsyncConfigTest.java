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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @Test
    void usesRocketMqImplementationsWhenModeIsRocketMqAndTemplateExists() {
        PlatformProperties properties = new PlatformProperties();
        properties.getAsync().setMode("rocketmq");
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            MemoryEventPublisher memoryEventPublisher = asyncConfig.memoryEventPublisher(
                    properties,
                    executorService,
                    mock(MemoryEventProcessor.class),
                    new StaticObjectProvider<>(rocketMQTemplate)
            );
            TaskDispatcher taskDispatcher = asyncConfig.taskDispatcher(
                    properties,
                    executorService,
                    new StaticObjectProvider<>(mock(TaskProcessor.class)),
                    new StaticObjectProvider<>(rocketMQTemplate)
            );
            LongTermMemoryJobDispatcher longTermMemoryJobDispatcher = asyncConfig.longTermMemoryJobDispatcher(
                    properties,
                    executorService,
                    new StaticObjectProvider<>(mock(LongTermMemoryJobProcessor.class)),
                    new StaticObjectProvider<>(rocketMQTemplate)
            );

            assertThat(memoryEventPublisher).isInstanceOf(RocketMqMemoryEventPublisher.class);
            assertThat(taskDispatcher).isInstanceOf(RocketMqTaskDispatcher.class);
            assertThat(longTermMemoryJobDispatcher).isInstanceOf(RocketMqLongTermMemoryJobDispatcher.class);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void fallsBackToLocalImplementationsWhenRocketMqTemplateIsUnavailable() {
        PlatformProperties properties = new PlatformProperties();
        properties.getAsync().setMode("rocketmq");
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            MemoryEventPublisher memoryEventPublisher = asyncConfig.memoryEventPublisher(
                    properties,
                    executorService,
                    mock(MemoryEventProcessor.class),
                    new StaticObjectProvider<>(null)
            );
            TaskDispatcher taskDispatcher = asyncConfig.taskDispatcher(
                    properties,
                    executorService,
                    new StaticObjectProvider<>(mock(TaskProcessor.class)),
                    new StaticObjectProvider<>(null)
            );
            LongTermMemoryJobDispatcher longTermMemoryJobDispatcher = asyncConfig.longTermMemoryJobDispatcher(
                    properties,
                    executorService,
                    new StaticObjectProvider<>(mock(LongTermMemoryJobProcessor.class)),
                    new StaticObjectProvider<>(null)
            );

            assertThat(memoryEventPublisher).isInstanceOf(LocalMemoryEventPublisher.class);
            assertThat(taskDispatcher).isInstanceOf(LocalTaskDispatcher.class);
            assertThat(longTermMemoryJobDispatcher).isInstanceOf(LocalLongTermMemoryJobDispatcher.class);
        } finally {
            executorService.shutdownNow();
        }
    }

    private static final class StaticObjectProvider<T> implements ObjectProvider<T> {

        private final T object;

        private StaticObjectProvider(T object) {
            this.object = object;
        }

        @Override
        public T getObject(Object... args) {
            if (object == null) {
                throw new IllegalStateException("Object not available");
            }
            return object;
        }

        @Override
        public T getIfAvailable() {
            return object;
        }

        @Override
        public T getIfUnique() {
            return object;
        }

        @Override
        public T getObject() {
            if (object == null) {
                throw new IllegalStateException("Object not available");
            }
            return object;
        }

        @Override
        public Iterator<T> iterator() {
            return object == null ? Collections.emptyIterator() : Collections.singleton(object).iterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            if (object != null) {
                action.accept(object);
            }
        }
    }
}
