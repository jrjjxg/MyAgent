package com.xg.platform.api.config;

import com.google.genai.Client;
import com.xg.platform.api.ai.ConfiguredProviderClientResolver;
import com.xg.platform.api.ai.ChatClientAgentTurnExecutionSupport;
import com.xg.platform.api.ai.ProviderClientResolver;
import com.xg.platform.api.ai.SpringAiAgentTurnExecutionSupport;
import com.xg.platform.api.ai.SpringAiPromptService;
import com.xg.platform.api.model.ModelProviderConfigService;
import com.xg.platform.agent.core.AgentPromptService;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    ToolCallingManager springAiToolCallingManager() {
        return DefaultToolCallingManager.builder()
                .observationRegistry(ObservationRegistry.NOOP)
                .toolCallbackResolver(new StaticToolCallbackResolver(List.of()))
                .build();
    }

    @Bean
    AgentPromptService agentPromptService() {
        return new SpringAiPromptService();
    }

    @Bean
    ProviderClientResolver providerClientResolver(ModelProviderConfigService modelProviderConfigService,
                                                  ToolCallingManager springAiToolCallingManager) {
        return new ConfiguredProviderClientResolver(modelProviderConfigService, springAiToolCallingManager);
    }

    @Bean
    AgentTurnExecutionSupport agentTurnExecutionSupport(AgentToolService agentToolService,
                                                        ProviderClientResolver providerClientResolver,
                                                        PlatformProperties properties) {
        String impl = properties.getAi().getExecution().getImpl();
        if ("chatclient".equalsIgnoreCase(impl)) {
            return new ChatClientAgentTurnExecutionSupport(
                    properties.getModel().getDefaultProvider(),
                    providerClientResolver,
                    agentToolService,
                    properties.getDebug().isLogPrompts(),
                    properties.getDebug().isLogAgentFlow(),
                    properties.getDebug().isLogModelResponses()
            );
        }
        if (impl == null || impl.isBlank() || "legacy".equalsIgnoreCase(impl)) {
            return new SpringAiAgentTurnExecutionSupport(
                    properties.getModel().getDefaultProvider(),
                    providerClientResolver,
                    agentToolService,
                    properties.getDebug().isLogPrompts(),
                    properties.getDebug().isLogAgentFlow(),
                    properties.getDebug().isLogModelResponses()
            );
        }
        throw new IllegalArgumentException("Unsupported platform.ai.execution.impl: " + impl);
    }
}
