package com.xg.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.AgentExecutionService;
import com.xg.platform.agent.core.AgentOrchestrator;
import com.xg.platform.agent.core.AgentPromptService;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.ConversationResponder;
import com.xg.platform.agent.core.DefaultConversationResponder;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.chat.ChatFlowService;
import com.xg.platform.agent.core.chat.ChatRouterService;
import com.xg.platform.agent.core.interaction.InteractionGraphNodeService;
import com.xg.platform.agent.core.research.execution.DefaultResearchExecutionSupport;
import com.xg.platform.agent.core.research.execution.ResearchExecutionFlowService;
import com.xg.platform.agent.core.research.execution.ResearchExecutionGraphNodeService;
import com.xg.platform.agent.core.research.execution.ResearchExecutionSupport;
import com.xg.platform.agent.core.research.scoping.ResearchScopingFlowService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.graph.CheckpointConfiguration;
import com.xg.platform.graph.GraphRuntimeFactory;
import com.xg.platform.graph.InteractionState;
import com.xg.platform.graph.ResearchTaskState;
import com.xg.platform.graph.RunEventConsumerRegistry;
import com.xg.platform.memory.ChunkIndexStore;
import com.xg.platform.memory.ContextAssembler;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.runtime.LongTermMemoryRepository;
import com.xg.platform.runtime.MemoryEventPublisher;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.ResearchDraftRepository;
import com.xg.platform.runtime.ResearchTaskSnapshotRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.TaskDispatcher;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.ThreadMemorySnapshotRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.tools.SkillRegistry;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Configuration(proxyBeanMethods = false)
public class GraphConfig {

    @Bean
    ChatFlowService chatFlowService(ThreadRuntimeService threadRuntimeService,
                                    MessageRepository messageRepository,
                                    RunEventRepository runEventRepository,
                                    MemoryEventPublisher memoryEventPublisher,
                                    ArtifactService artifactService,
                                    WorkspaceManager workspaceManager,
                                    DocumentStore documentStore,
                                    ChatRouterService chatRouterService,
                                    ConversationResponder conversationResponder,
                                    AgentTurnExecutionSupport agentTurnExecutionSupport,
                                    PlatformProperties properties) {
        return new ChatFlowService(
                threadRuntimeService,
                messageRepository,
                runEventRepository,
                memoryEventPublisher,
                artifactService,
                workspaceManager,
                documentStore,
                chatRouterService,
                conversationResponder,
                agentTurnExecutionSupport,
                properties.getDebug().isLogAgentFlow()
        );
    }

    @Bean
    ChatRouterService chatRouterService() {
        return new ChatRouterService();
    }

    @Bean
    ResearchScopingFlowService researchScopingFlowService(ThreadRuntimeService threadRuntimeService,
                                                          MessageRepository messageRepository,
                                                          ResearchDraftRepository researchDraftRepository,
                                                          RunEventRepository runEventRepository,
                                                          MemoryEventPublisher memoryEventPublisher,
                                                          ArtifactService artifactService,
                                                          AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                          ObjectMapper objectMapper,
                                                          PlatformProperties properties) {
        return new ResearchScopingFlowService(
                threadRuntimeService,
                messageRepository,
                researchDraftRepository,
                runEventRepository,
                memoryEventPublisher,
                artifactService,
                agentTurnExecutionSupport,
                objectMapper.copy(),
                properties.getDebug().isLogAgentFlow()
        );
    }

    @Bean
    InteractionGraphNodeService interactionGraphNodeService(ConversationMemoryService conversationMemoryService,
                                                            LongTermMemoryRepository longTermMemoryRepository,
                                                            MemoryContextFormatter memoryContextFormatter,
                                                            ResearchDraftRepository researchDraftRepository,
                                                            ThreadRuntimeService threadRuntimeService,
                                                            ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                                            MessageRepository messageRepository,
                                                            RunEventRepository runEventRepository,
                                                            MemoryEventPublisher memoryEventPublisher,
                                                            ArtifactService artifactService,
                                                            WorkspaceManager workspaceManager,
                                                            DocumentStore documentStore,
                                                            ContextAssembler contextAssembler,
                                                            DocumentIngestService documentIngestService,
                                                            ChatRouterService chatRouterService,
                                                            AgentPromptService agentPromptService,
                                                            AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                            AgentToolService agentToolService,
                                                            SkillRegistry skillRegistry,
                                                            ResearchScopingFlowService researchScopingFlowService,
                                                            RunEventConsumerRegistry runEventConsumerRegistry,
                                                            ObjectMapper objectMapper,
                                                            PlatformProperties properties) {
        PlatformProperties.ToolAssisted toolAssisted = properties.getChat().getToolAssisted();
        return new InteractionGraphNodeService(
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
                researchScopingFlowService,
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
    ResearchExecutionSupport researchExecutionSupport(SkillRegistry skillRegistry,
                                                      AgentToolService agentToolService,
                                                      DocumentStore documentStore,
                                                      DocumentIngestService documentIngestService,
                                                      ChunkIndexStore chunkIndexStore,
                                                      ContextAssembler contextAssembler,
                                                      AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                      ObjectMapper objectMapper,
                                                      PlatformProperties properties) {
        PlatformProperties.UnitAgent unitAgent = properties.getResearch().getUnitAgent();
        return new DefaultResearchExecutionSupport(
                skillRegistry,
                agentToolService,
                documentStore,
                documentIngestService,
                chunkIndexStore,
                contextAssembler,
                agentTurnExecutionSupport,
                objectMapper.copy(),
                properties.getDebug().isLogAgentFlow(),
                properties.getDebug().isLogModelResponses(),
                unitAgent.getMaxToolCalls(),
                unitAgent.getMaxSearchCalls(),
                unitAgent.getMaxFetchCalls(),
                unitAgent.getReflectionAfterSearches(),
                unitAgent.getMinVerifiedSources(),
                unitAgent.getTimeoutMs()
        );
    }

    @Bean
    ResearchExecutionFlowService researchExecutionFlowService(ThreadRuntimeService threadRuntimeService,
                                                              TaskRepository taskRepository,
                                                              RunEventRepository runEventRepository,
                                                              MessageRepository messageRepository,
                                                              MemoryEventPublisher memoryEventPublisher,
                                                              ArtifactService artifactService,
                                                              WorkspaceManager workspaceManager,
                                                              ResearchExecutionSupport researchExecutionSupport,
                                                              AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                              AgentToolService agentToolService,
                                                              ResearchTaskSnapshotRepository researchTaskSnapshotRepository,
                                                              ObjectMapper objectMapper,
                                                              PlatformProperties properties) {
        PlatformProperties.ResearchExecution execution = properties.getResearch().getExecution();
        return new ResearchExecutionFlowService(
                threadRuntimeService,
                taskRepository,
                runEventRepository,
                messageRepository,
                memoryEventPublisher,
                artifactService,
                workspaceManager,
                researchExecutionSupport,
                agentTurnExecutionSupport,
                agentToolService,
                researchTaskSnapshotRepository,
                objectMapper.copy(),
                properties.getDebug().isLogAgentFlow(),
                execution.getMaxIterations(),
                execution.getMaxWallTimeMs()
        );
    }

    @Bean
    ResearchExecutionGraphNodeService researchExecutionGraphNodeService(TaskRepository taskRepository,
                                                                        ThreadRuntimeService threadRuntimeService,
                                                                        ConversationMemoryService conversationMemoryService,
                                                                        LongTermMemoryRepository longTermMemoryRepository,
                                                                        MemoryContextFormatter memoryContextFormatter,
                                                                        ResearchExecutionFlowService researchExecutionFlowService) {
        return new ResearchExecutionGraphNodeService(
                taskRepository,
                threadRuntimeService,
                conversationMemoryService,
                longTermMemoryRepository,
                memoryContextFormatter,
                researchExecutionFlowService
        );
    }

    @Bean
    AgentOrchestrator agentOrchestrator(AgentPromptService agentPromptService,
                                        SkillRegistry skillRegistry,
                                        AgentToolService agentToolService,
                                        ChatRouterService chatRouterService,
                                        AgentTurnExecutionSupport agentTurnExecutionSupport,
                                        DocumentStore documentStore,
                                        DocumentIngestService documentIngestService,
                                        ChunkIndexStore chunkIndexStore,
                                        ContextAssembler contextAssembler,
                                        ArtifactService artifactService,
                                        WorkspaceManager workspaceManager,
                                        ThreadRuntimeService threadRuntimeService,
                                        ResearchExecutionSupport researchExecutionSupport,
                                        ObjectMapper objectMapper,
                                        PlatformProperties properties) {
        PlatformProperties.ToolAssisted toolAssisted = properties.getChat().getToolAssisted();
        return new AgentOrchestrator(
                agentPromptService,
                skillRegistry,
                agentToolService,
                chatRouterService,
                agentTurnExecutionSupport,
                documentStore,
                documentIngestService,
                chunkIndexStore,
                contextAssembler,
                artifactService,
                workspaceManager,
                threadRuntimeService,
                researchExecutionSupport,
                objectMapper.copy(),
                properties.getDebug().isLogPrompts(),
                properties.getDebug().isLogAgentFlow(),
                properties.getDebug().isLogModelResponses(),
                toolAssisted.getMaxToolCalls(),
                toolAssisted.getMaxSearchCalls(),
                toolAssisted.getMaxFetchCalls(),
                toolAssisted.getMinVerifiedSources(),
                toolAssisted.getTimeoutMs()
        );
    }

    @Bean
    ConversationResponder conversationResponder(AgentOrchestrator agentOrchestrator) {
        return new DefaultConversationResponder(agentOrchestrator);
    }

    @Bean
    RunEventConsumerRegistry runEventConsumerRegistry() {
        return new RunEventConsumerRegistry();
    }

    @Bean
    CompiledGraph<InteractionState> interactionCompiledGraph(CheckpointConfiguration checkpointConfiguration,
                                                             InteractionGraphNodeService interactionGraphNodeService) {
        return GraphRuntimeFactory.compileInteractionGraph(checkpointConfiguration, interactionGraphNodeService);
    }

    @Bean
    CompiledGraph<ResearchTaskState> researchCompiledGraph(CheckpointConfiguration checkpointConfiguration,
                                                           ResearchExecutionGraphNodeService researchExecutionGraphNodeService) {
        return GraphRuntimeFactory.compileResearchGraph(checkpointConfiguration, researchExecutionGraphNodeService);
    }

    @Bean
    GraphRuntimeFactory graphRuntimeFactory(CompiledGraph<InteractionState> interactionCompiledGraph,
                                            CompiledGraph<ResearchTaskState> researchCompiledGraph,
                                            RunEventConsumerRegistry runEventConsumerRegistry) {
        return new GraphRuntimeFactory(
                interactionCompiledGraph,
                researchCompiledGraph,
                runEventConsumerRegistry
        );
    }

    @Bean
    AgentExecutionService agentExecutionService(ThreadRuntimeService threadRuntimeService,
                                                TaskRepository taskRepository,
                                                RunEventRepository runEventRepository,
                                                MemoryEventPublisher memoryEventPublisher,
                                                MessageRepository messageRepository,
                                                ResearchDraftRepository researchDraftRepository,
                                                ResearchTaskSnapshotRepository researchTaskSnapshotRepository,
                                                WorkspaceManager workspaceManager,
                                                AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                TaskDispatcher taskDispatcher,
                                                DocumentIngestService documentIngestService,
                                                GraphRuntimeFactory graphRuntimeFactory,
                                                ObjectMapper objectMapper,
                                                PlatformProperties properties) {
        return new AgentExecutionService(
                threadRuntimeService,
                taskRepository,
                runEventRepository,
                memoryEventPublisher,
                messageRepository,
                researchDraftRepository,
                researchTaskSnapshotRepository,
                workspaceManager,
                agentTurnExecutionSupport,
                taskDispatcher,
                documentIngestService,
                graphRuntimeFactory,
                objectMapper.copy(),
                properties.getDebug().isLogAgentFlow()
        );
    }
}
