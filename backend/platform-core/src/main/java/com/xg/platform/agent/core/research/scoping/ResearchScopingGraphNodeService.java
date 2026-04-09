package com.xg.platform.agent.core.research.scoping;

import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.contracts.message.ResearchDraftRecord;
import com.xg.platform.contracts.message.ResearchDraftStatus;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.graph.ResearchScopingGraphNodes;
import com.xg.platform.graph.ResearchScopingState;
import com.xg.platform.graph.RunEventConsumerRegistry;
import com.xg.platform.runtime.LongTermMemoryRepository;
import com.xg.platform.runtime.ResearchDraftRepository;

import java.util.Map;
import java.util.function.Consumer;

public class ResearchScopingGraphNodeService implements ResearchScopingGraphNodes {

    private final ConversationMemoryService conversationMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final MemoryContextFormatter memoryContextFormatter;
    private final ResearchDraftRepository researchDraftRepository;
    private final ResearchScopingFlowService researchScopingFlowService;
    private final RunEventConsumerRegistry runEventConsumerRegistry;

    public ResearchScopingGraphNodeService(ConversationMemoryService conversationMemoryService,
                                           LongTermMemoryRepository longTermMemoryRepository,
                                           MemoryContextFormatter memoryContextFormatter,
                                           ResearchDraftRepository researchDraftRepository,
                                           ResearchScopingFlowService researchScopingFlowService,
                                           RunEventConsumerRegistry runEventConsumerRegistry) {
        this.conversationMemoryService = conversationMemoryService;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.memoryContextFormatter = memoryContextFormatter;
        this.researchDraftRepository = researchDraftRepository;
        this.researchScopingFlowService = researchScopingFlowService;
        this.runEventConsumerRegistry = runEventConsumerRegistry;
    }

    @Override
    public Map<String, Object> loadShortTermMemory(ResearchScopingState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ThreadMemoryView memoryView = conversationMemoryService.threadMemoryView(userId, threadId);
        return Map.of(
                ResearchScopingState.MEMORY_VIEW, memoryView,
                ResearchScopingState.SESSION_SUMMARY, memoryView.summary()
        );
    }

    @Override
    public Map<String, Object> loadLongTermMemory(ResearchScopingState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        return Map.of(
                ResearchScopingState.LONG_TERM_MEMORY,
                memoryContextFormatter.formatLongTermMemory(longTermMemoryRepository.listActive(userId), threadId)
        );
    }

    @Override
    public Map<String, Object> loadCurrentDraft(ResearchScopingState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ResearchDraftRecord draft = researchDraftRepository.findActiveDraft(userId, threadId).orElse(null);
        if (draft == null || draft.status() == ResearchDraftStatus.STARTED) {
            return Map.of();
        }
        return Map.of(ResearchScopingState.CURRENT_DRAFT, draft);
    }

    @Override
    public Map<String, Object> runScopingFrame(ResearchScopingState state) {
        return runScopingFrame(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> runScopingFrame(ResearchScopingState state, Consumer<RunEvent> runEventConsumer) {
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Research scoping request is missing"));
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView()
                .orElseGet(() -> conversationMemoryService.threadMemoryView(
                        state.userId().orElseThrow(),
                        state.threadId().orElseThrow()
                ));
        return researchScopingFlowService.runScopingFrame(
                state.userId().orElseThrow(),
                state.threadId().orElseThrow(),
                request,
                memoryView,
                state.longTermMemory().orElse(""),
                state.<ResearchDraftRecord>currentDraft().orElse(null),
                runEventConsumer
        );
    }

    @Override
    public Map<String, Object> persistAssistantMessage(ResearchScopingState state) {
        return persistAssistantMessage(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> persistDraft(ResearchScopingState state) {
        return researchScopingFlowService.persistDraft(state);
    }

    @Override
    public Map<String, Object> persistAssistantMessage(ResearchScopingState state, Consumer<RunEvent> runEventConsumer) {
        return researchScopingFlowService.persistAssistantMessage(state, runEventConsumer);
    }

    @Override
    public Map<String, Object> publishScopingEvents(ResearchScopingState state) {
        return publishScopingEvents(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> publishScopingEvents(ResearchScopingState state, Consumer<RunEvent> runEventConsumer) {
        return researchScopingFlowService.publishScopingEvents(state, runEventConsumer);
    }
}
