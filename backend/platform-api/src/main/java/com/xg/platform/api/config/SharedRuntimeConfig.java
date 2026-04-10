package com.xg.platform.api.config;

import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.conversation.runtime.InteractionState;
import com.xg.platform.research.application.ResearchTaskExecutionService;
import com.xg.platform.research.runtime.ResearchTaskState;
import com.xg.platform.shared.runtime.async.PlatformTaskProcessor;
import com.xg.platform.shared.runtime.async.TaskProcessor;
import com.xg.platform.shared.runtime.graph.PlatformGraphRunner;
import com.xg.platform.shared.runtime.graph.RunEventConsumerRegistry;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SharedRuntimeConfig {

    @Bean
    RunEventConsumerRegistry runEventConsumerRegistry() {
        return new RunEventConsumerRegistry();
    }

    @Bean
    PlatformGraphRunner platformGraphRunner(CompiledGraph<InteractionState> interactionCompiledGraph,
                                            CompiledGraph<ResearchTaskState> researchCompiledGraph,
                                            RunEventConsumerRegistry runEventConsumerRegistry) {
        return new PlatformGraphRunner(
                interactionCompiledGraph,
                researchCompiledGraph,
                runEventConsumerRegistry
        );
    }

    @Bean
    TaskProcessor taskProcessor(DocumentIngestService documentIngestService,
                                ResearchTaskExecutionService researchTaskExecutionService) {
        return new PlatformTaskProcessor(documentIngestService, researchTaskExecutionService);
    }
}
