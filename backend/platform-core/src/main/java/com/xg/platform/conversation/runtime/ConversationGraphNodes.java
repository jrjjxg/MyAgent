package com.xg.platform.conversation.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.AgentPromptService;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.conversation.application.ConversationRouterService;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.document.application.ContextAssembler;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.memory.port.LongTermMemoryRepository;
import com.xg.platform.memory.port.MemoryEventPublisher;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;
import com.xg.platform.research.application.ResearchDraftScopingService;
import com.xg.platform.research.port.ResearchDraftRepository;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.runtime.graph.RunEventConsumerRegistry;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.WorkspaceManager;

import java.util.Map;
import java.util.function.Consumer;

public class ConversationGraphNodes implements InteractionGraphNodes {

    private final ConversationMemoryNodes memoryNodes;
    private final ConversationRoutingNodes routingNodes;
    private final ConversationAgentNodes agentNodes;
    private final ConversationPersistenceNodes persistenceNodes;

    public ConversationGraphNodes(ConversationMemoryService conversationMemoryService,
                                  LongTermMemoryRepository longTermMemoryRepository,
                                  MemoryContextFormatter memoryContextFormatter,
                                  ResearchDraftRepository researchDraftRepository,
                                  ThreadService threadService,
                                  ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                  MessageRepository messageRepository,
                                  RunEventRepository runEventRepository,
                                  MemoryEventPublisher memoryEventPublisher,
                                  ArtifactService artifactService,
                                  WorkspaceManager workspaceManager,
                                  DocumentStore documentStore,
                                  ContextAssembler contextAssembler,
                                  DocumentIngestService documentIngestService,
                                  ConversationRouterService conversationRouterService,
                                  AgentPromptService agentPromptService,
                                  AgentTurnExecutionSupport agentTurnExecutionSupport,
                                  AgentToolService agentToolService,
                                  SkillRegistry skillRegistry,
                                  ResearchDraftScopingService researchDraftScopingService,
                                  RunEventConsumerRegistry runEventConsumerRegistry,
                                  ObjectMapper objectMapper,
                                  boolean logAgentFlow,
                                  int maxToolCalls,
                                  int maxSearchCalls,
                                  int maxFetchCalls,
                                  int minVerifiedSources,
                                  long timeoutMs) {
        ConversationRuntimeSupport support = new ConversationRuntimeSupport(
                conversationMemoryService,
                longTermMemoryRepository,
                memoryContextFormatter,
                researchDraftRepository,
                threadService,
                threadMemorySnapshotRepository,
                messageRepository,
                runEventRepository,
                memoryEventPublisher,
                artifactService,
                workspaceManager,
                documentStore,
                contextAssembler,
                documentIngestService,
                conversationRouterService,
                agentPromptService,
                agentTurnExecutionSupport,
                agentToolService,
                skillRegistry,
                researchDraftScopingService,
                runEventConsumerRegistry,
                objectMapper,
                logAgentFlow,
                maxToolCalls,
                maxSearchCalls,
                maxFetchCalls,
                minVerifiedSources,
                timeoutMs
        );
        this.memoryNodes = new ConversationMemoryNodes(support);
        this.routingNodes = new ConversationRoutingNodes(support);
        this.agentNodes = new ConversationAgentNodes(support);
        this.persistenceNodes = new ConversationPersistenceNodes(support);
    }

    @Override
    public Map<String, Object> loadShortTermMemory(InteractionState state) {
        return memoryNodes.loadShortTermMemory(state);
    }

    @Override
    public Map<String, Object> loadLongTermMemory(InteractionState state) {
        return memoryNodes.loadLongTermMemory(state);
    }

    @Override
    public Map<String, Object> loadDraftContext(InteractionState state) {
        return memoryNodes.loadDraftContext(state);
    }

    @Override
    public Map<String, Object> routeInteraction(InteractionState state) {
        return routingNodes.routeInteraction(state);
    }

    @Override
    public Map<String, Object> prepareAgentStep(InteractionState state) {
        return agentNodes.prepareAgentStep(state);
    }

    @Override
    public Map<String, Object> prepareAgentStep(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return agentNodes.prepareAgentStep(state, runEventConsumer);
    }

    @Override
    public Map<String, Object> agent(InteractionState state) {
        return agentNodes.agent(state);
    }

    @Override
    public Map<String, Object> executeTools(InteractionState state) {
        return agentNodes.executeTools(state);
    }

    @Override
    public Map<String, Object> executeTools(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return agentNodes.executeTools(state, runEventConsumer);
    }

    @Override
    public Map<String, Object> runScopingFrame(InteractionState state) {
        return persistenceNodes.runScopingFrame(state);
    }

    @Override
    public Map<String, Object> runScopingFrame(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return persistenceNodes.runScopingFrame(state, runEventConsumer);
    }

    @Override
    public Map<String, Object> persistDraft(InteractionState state) {
        return persistenceNodes.persistDraft(state);
    }

    @Override
    public Map<String, Object> persistAssistantMessage(InteractionState state) {
        return persistenceNodes.persistAssistantMessage(state);
    }

    @Override
    public Map<String, Object> persistAssistantMessage(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return persistenceNodes.persistAssistantMessage(state, runEventConsumer);
    }

    @Override
    public Map<String, Object> persistTurnArtifacts(InteractionState state) {
        return persistenceNodes.persistTurnArtifacts(state);
    }

    @Override
    public Map<String, Object> persistTurnArtifacts(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return persistenceNodes.persistTurnArtifacts(state, runEventConsumer);
    }

    @Override
    public Map<String, Object> publishTurnEvents(InteractionState state) {
        return persistenceNodes.publishTurnEvents(state);
    }

    @Override
    public Map<String, Object> publishTurnEvents(InteractionState state, Consumer<RunEvent> runEventConsumer) {
        return persistenceNodes.publishTurnEvents(state, runEventConsumer);
    }
}
