package com.xg.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.AgentPromptService;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.conversation.application.ConversationCommandService;
import com.xg.platform.conversation.application.ConversationRouterService;
import com.xg.platform.conversation.runtime.ConversationGraphDefinition;
import com.xg.platform.conversation.runtime.ConversationGraphNodes;
import com.xg.platform.conversation.runtime.InteractionState;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.document.application.ContextAssembler;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.memory.port.LongTermMemoryRepository;
import com.xg.platform.memory.port.MemoryEventPublisher;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;
import com.xg.platform.research.application.ResearchDraftScopingService;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.shared.runtime.graph.CheckpointConfiguration;
import com.xg.platform.shared.runtime.graph.PlatformGraphRunner;
import com.xg.platform.shared.runtime.graph.RunEventConsumerRegistry;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConversationConfig {

    @Bean
    ConversationRouterService chatRouterService() {
        return new ConversationRouterService();
    }

    @Bean
    ConversationGraphNodes conversationGraphNodes(ConversationMemoryService conversationMemoryService,
                                                  LongTermMemoryRepository longTermMemoryRepository,
                                                  MemoryContextFormatter memoryContextFormatter,
                                                  ResearchDraftRepository researchDraftRepository,
                                                  ThreadService threadRuntimeService,
                                                  ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                                  MessageRepository messageRepository,
                                                  RunEventRepository runEventRepository,
                                                  MemoryEventPublisher memoryEventPublisher,
                                                  ArtifactService artifactService,
                                                  WorkspaceManager workspaceManager,
                                                  DocumentStore documentStore,
                                                  ContextAssembler contextAssembler,
                                                  DocumentIngestService documentIngestService,
                                                  ConversationRouterService chatRouterService,
                                                  AgentPromptService agentPromptService,
                                                  AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                  AgentToolService agentToolService,
                                                  SkillRegistry skillRegistry,
                                                  ResearchDraftScopingService researchDraftScopingService,
                                                  RunEventConsumerRegistry runEventConsumerRegistry,
                                                  ObjectMapper objectMapper,
                                                  PlatformProperties properties) {
        PlatformProperties.ToolAssisted toolAssisted = properties.getChat().getToolAssisted();
        return new ConversationGraphNodes(
                conversationMemoryService,
                longTermMemoryRepository,
                memoryContextFormatter,
                researchDraftRepository,
                threadRuntimeService,
                threadMemorySnapshotRepository,
                messageRepository,
                runEventRepository,
                memoryEventPublisher,
                artifactService,
                workspaceManager,
                documentStore,
                contextAssembler,
                documentIngestService,
                chatRouterService,
                agentPromptService,
                agentTurnExecutionSupport,
                agentToolService,
                skillRegistry,
                researchDraftScopingService,
                runEventConsumerRegistry,
                objectMapper.copy(),
                properties.getDebug().isLogAgentFlow(),
                toolAssisted.getMaxToolCalls(),
                toolAssisted.getMaxSearchCalls(),
                toolAssisted.getMaxFetchCalls(),
                toolAssisted.getMinVerifiedSources(),
                toolAssisted.getTimeoutMs()
        );
    }

    @Bean
    CompiledGraph<InteractionState> interactionCompiledGraph(CheckpointConfiguration checkpointConfiguration,
                                                             ConversationGraphNodes conversationGraphNodes) {
        return ConversationGraphDefinition.compile(checkpointConfiguration, conversationGraphNodes);
    }

    @Bean
    ConversationCommandService conversationCommandService(ThreadService threadRuntimeService,
                                                          TaskRepository taskRepository,
                                                          RunEventRepository runEventRepository,
                                                          MessageRepository messageRepository,
                                                          WorkspaceManager workspaceManager,
                                                          AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                          DocumentIngestService documentIngestService,
                                                          PlatformGraphRunner platformGraphRunner,
                                                          PlatformProperties properties) {
        return new ConversationCommandService(
                threadRuntimeService,
                taskRepository,
                runEventRepository,
                messageRepository,
                workspaceManager,
                agentTurnExecutionSupport,
                documentIngestService,
                platformGraphRunner,
                properties.getDebug().isLogAgentFlow()
        );
    }
}
