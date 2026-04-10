package com.xg.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.research.execution.DefaultResearchExecutionSupport;
import com.xg.platform.research.runtime.ResearchExecutionGraphNodeService;
import com.xg.platform.agent.core.research.execution.ResearchExecutionSupport;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.document.application.ChunkIndexStore;
import com.xg.platform.document.application.ContextAssembler;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.memory.port.LongTermMemoryRepository;
import com.xg.platform.memory.port.MemoryEventPublisher;
import com.xg.platform.research.application.ResearchCommandService;
import com.xg.platform.research.application.ResearchDraftScopingService;
import com.xg.platform.research.application.ResearchReadService;
import com.xg.platform.research.application.ResearchTaskExecutionService;
import com.xg.platform.research.application.ResearchWorkflowService;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.research.port.ResearchTaskSnapshotRepository;
import com.xg.platform.research.runtime.ResearchGraphDefinition;
import com.xg.platform.research.runtime.ResearchTaskState;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.shared.runtime.async.TaskDispatcher;
import com.xg.platform.shared.runtime.graph.CheckpointConfiguration;
import com.xg.platform.shared.runtime.graph.PlatformGraphRunner;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ResearchConfig {

    @Bean
    ResearchDraftScopingService researchDraftScopingService(ThreadService threadRuntimeService,
                                                            MessageRepository messageRepository,
                                                            ResearchDraftRepository researchDraftRepository,
                                                            RunEventRepository runEventRepository,
                                                            MemoryEventPublisher memoryEventPublisher,
                                                            ArtifactService artifactService,
                                                            AgentTurnExecutionSupport agentTurnExecutionSupport,
                                                            ObjectMapper objectMapper,
                                                            PlatformProperties properties) {
        return new ResearchDraftScopingService(
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
                new DefaultResearchExecutionSupport.Limits(
                        unitAgent.getMaxToolCalls(),
                        unitAgent.getMaxSearchCalls(),
                        unitAgent.getMaxFetchCalls(),
                        unitAgent.getReflectionAfterSearches(),
                        unitAgent.getMinVerifiedSources(),
                        unitAgent.getTimeoutMs()
                )
        );
    }

    @Bean
    ResearchWorkflowService researchWorkflowService(ThreadService threadRuntimeService,
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
        return new ResearchWorkflowService(
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
                                                                        ThreadService threadRuntimeService,
                                                                        ConversationMemoryService conversationMemoryService,
                                                                        LongTermMemoryRepository longTermMemoryRepository,
                                                                        MemoryContextFormatter memoryContextFormatter,
                                                                        ResearchWorkflowService researchWorkflowService) {
        return new ResearchExecutionGraphNodeService(
                taskRepository,
                threadRuntimeService,
                conversationMemoryService,
                longTermMemoryRepository,
                memoryContextFormatter,
                researchWorkflowService
        );
    }

    @Bean
    CompiledGraph<ResearchTaskState> researchCompiledGraph(CheckpointConfiguration checkpointConfiguration,
                                                           ResearchExecutionGraphNodeService researchExecutionGraphNodeService) {
        return ResearchGraphDefinition.compile(checkpointConfiguration, researchExecutionGraphNodeService);
    }

    @Bean
    ResearchCommandService researchCommandService(ThreadService threadRuntimeService,
                                                  TaskRepository taskRepository,
                                                  RunEventRepository runEventRepository,
                                                  MemoryEventPublisher memoryEventPublisher,
                                                  MessageRepository messageRepository,
                                                  ResearchDraftRepository researchDraftRepository,
                                                  ResearchTaskSnapshotRepository researchTaskSnapshotRepository,
                                                  TaskDispatcher taskDispatcher,
                                                  ObjectMapper objectMapper,
                                                  PlatformProperties properties) {
        return new ResearchCommandService(
                threadRuntimeService,
                taskRepository,
                runEventRepository,
                memoryEventPublisher,
                messageRepository,
                researchDraftRepository,
                researchTaskSnapshotRepository,
                taskDispatcher,
                objectMapper.copy(),
                properties.getDebug().isLogAgentFlow()
        );
    }

    @Bean
    ResearchTaskExecutionService researchTaskExecutionService(PlatformGraphRunner platformGraphRunner) {
        return new ResearchTaskExecutionService(platformGraphRunner);
    }

    @Bean
    ResearchReadService researchReadService(ThreadService threadRuntimeService,
                                            TaskRepository taskRepository,
                                            ResearchTaskSnapshotRepository researchTaskSnapshotRepository,
                                            WorkspaceManager workspaceManager,
                                            ObjectMapper objectMapper) {
        return new ResearchReadService(
                threadRuntimeService,
                taskRepository,
                researchTaskSnapshotRepository,
                workspaceManager,
                objectMapper.copy()
        );
    }
}
