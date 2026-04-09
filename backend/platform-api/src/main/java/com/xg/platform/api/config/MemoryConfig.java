package com.xg.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.cache.redis.RedisThreadMemoryViewCache;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.application.ConversationSummaryCompressor;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.application.DefaultMemoryEventProcessor;
import com.xg.platform.agent.core.application.DefaultLongTermMemoryJobProcessor;
import com.xg.platform.agent.core.application.LlmConversationSummaryCompressor;
import com.xg.platform.agent.core.application.LongTermMemoryExtractionService;
import com.xg.platform.agent.core.application.LongTermMemoryJobScheduler;
import com.xg.platform.agent.core.application.ShortTermMemoryProjectionService;
import com.xg.platform.agent.core.application.SimpleConversationSummaryCompressor;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.memory.NoOpThreadMemoryViewCache;
import com.xg.platform.runtime.LongTermMemoryJobDispatcher;
import com.xg.platform.runtime.LongTermMemoryJobProcessor;
import com.xg.platform.runtime.LongTermMemoryJobRepository;
import com.xg.platform.runtime.LongTermMemoryRepository;
import com.xg.platform.runtime.MemoryEventProcessor;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.ResearchDraftRepository;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.ThreadMemoryViewCache;
import com.xg.platform.runtime.ThreadMemorySnapshotRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class MemoryConfig {

    @Bean
    ThreadMemoryViewCache threadMemoryViewCache(PlatformProperties properties,
                                                ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
                                                ObjectMapper objectMapper) {
        if (properties.getMemory().getRedis().isEnabled() && stringRedisTemplateProvider.getIfAvailable() != null) {
            return new RedisThreadMemoryViewCache(
                    stringRedisTemplateProvider.getObject(),
                    objectMapper.copy(),
                    Duration.ofHours(properties.getMemory().getRedis().getTtlHours())
            );
        }
        return new NoOpThreadMemoryViewCache();
    }

    @Bean
    ConversationSummaryCompressor conversationSummaryCompressor(PlatformProperties properties,
                                                                AgentTurnExecutionSupport agentTurnExecutionSupport) {
        PlatformProperties.Summary summary = properties.getMemory().getShortTerm().getSummary();
        ConversationSummaryCompressor fallback = new SimpleConversationSummaryCompressor();
        if (!summary.isEnabled()) {
            return fallback;
        }
        return new LlmConversationSummaryCompressor(
                agentTurnExecutionSupport,
                summary.getProvider(),
                summary.getModel(),
                summary.getMaxMessagesPerChunk(),
                summary.getMaxCharsPerChunk(),
                summary.getMaxWords(),
                fallback,
                properties.getDebug().isLogAgentFlow()
        );
    }

    @Bean
    ShortTermMemoryProjectionService shortTermMemoryProjectionService(MessageRepository messageRepository,
                                                                      ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                                                      ThreadMemoryViewCache threadMemoryViewCache,
                                                                      ConversationSummaryCompressor conversationSummaryCompressor,
                                                                      ResearchDraftRepository researchDraftRepository,
                                                                      TaskRepository taskRepository,
                                                                      PlatformProperties properties) {
        return new ShortTermMemoryProjectionService(
                messageRepository,
                threadMemorySnapshotRepository,
                threadMemoryViewCache,
                conversationSummaryCompressor,
                researchDraftRepository,
                taskRepository,
                properties.getMemory().getShortTerm().getWindowSize()
        );
    }

    @Bean
    LongTermMemoryExtractionService longTermMemoryExtractionService(MessageRepository messageRepository,
                                                                    LongTermMemoryRepository longTermMemoryRepository,
                                                                    LongTermMemoryJobRepository longTermMemoryJobRepository,
                                                                    AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                                    ObjectMapper objectMapper,
                                                                    PlatformProperties properties) {
        return new LongTermMemoryExtractionService(
                messageRepository,
                longTermMemoryRepository,
                longTermMemoryJobRepository,
                agentTurnExecutionSupport,
                objectMapper.copy(),
                properties.getMemory().getLongTerm().getExtractionProvider(),
                properties.getMemory().getLongTerm().getExtractionModel(),
                properties.getMemory().getLongTerm().getExtractionVersion(),
                properties.getMemory().getLongTerm().getMaxContextMessages(),
                properties.getDebug().isLogAgentFlow()
        );
    }

    @Bean
    LongTermMemoryJobScheduler longTermMemoryJobScheduler(LongTermMemoryJobRepository longTermMemoryJobRepository,
                                                          LongTermMemoryJobDispatcher longTermMemoryJobDispatcher,
                                                          MessageRepository messageRepository,
                                                          PlatformProperties properties) {
        return new LongTermMemoryJobScheduler(
                longTermMemoryJobRepository,
                longTermMemoryJobDispatcher,
                messageRepository,
                properties.getMemory().getLongTerm().getExtractionVersion(),
                properties.getMemory().getLongTerm().getTurnInterval()
        );
    }

    @Bean
    LongTermMemoryJobProcessor longTermMemoryJobProcessor(LongTermMemoryJobRepository longTermMemoryJobRepository,
                                                          LongTermMemoryExtractionService longTermMemoryExtractionService,
                                                          PlatformProperties properties) {
        return new DefaultLongTermMemoryJobProcessor(
                longTermMemoryJobRepository,
                longTermMemoryExtractionService,
                properties.getMemory().getLongTerm().getMaxAttempts()
        );
    }

    @Bean
    MemoryEventProcessor memoryEventProcessor(ShortTermMemoryProjectionService shortTermMemoryProjectionService,
                                              LongTermMemoryJobScheduler longTermMemoryJobScheduler,
                                              PlatformProperties properties) {
        return new DefaultMemoryEventProcessor(
                shortTermMemoryProjectionService,
                longTermMemoryJobScheduler,
                properties.getMemory().getShortTerm().getProjectorDebounceMs()
        );
    }

    @Bean
    ConversationMemoryService conversationMemoryService(ThreadRuntimeService threadRuntimeService,
                                                        MessageRepository messageRepository,
                                                        ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                                        ThreadMemoryViewCache threadMemoryViewCache,
                                                        ShortTermMemoryProjectionService shortTermMemoryProjectionService,
                                                        PlatformProperties properties) {
        return new ConversationMemoryService(
                threadRuntimeService,
                messageRepository,
                threadMemorySnapshotRepository,
                threadMemoryViewCache,
                shortTermMemoryProjectionService,
                properties.getMemory().getShortTerm().getWindowSize(),
                properties.getMemory().getShortTerm().isReadModelAsync()
        );
    }

    @Bean
    MemoryContextFormatter memoryContextFormatter() {
        return new MemoryContextFormatter();
    }
}
