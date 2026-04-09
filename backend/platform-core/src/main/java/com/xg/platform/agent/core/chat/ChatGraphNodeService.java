package com.xg.platform.agent.core.chat;

import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.agent.core.shared.MemoryContextFormatter;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.graph.ChatGraphNodes;
import com.xg.platform.graph.ChatState;
import com.xg.platform.graph.RunEventConsumerRegistry;
import com.xg.platform.runtime.LongTermMemoryRepository;

import java.util.Map;
import java.util.function.Consumer;

public class ChatGraphNodeService implements ChatGraphNodes {

    private final ConversationMemoryService conversationMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final MemoryContextFormatter memoryContextFormatter;
    private final ChatFlowService chatFlowService;
    private final RunEventConsumerRegistry runEventConsumerRegistry;

    public ChatGraphNodeService(ConversationMemoryService conversationMemoryService,
                                LongTermMemoryRepository longTermMemoryRepository,
                                MemoryContextFormatter memoryContextFormatter,
                                ChatFlowService chatFlowService,
                                RunEventConsumerRegistry runEventConsumerRegistry) {
        this.conversationMemoryService = conversationMemoryService;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.memoryContextFormatter = memoryContextFormatter;
        this.chatFlowService = chatFlowService;
        this.runEventConsumerRegistry = runEventConsumerRegistry;
    }

    @Override
    public Map<String, Object> loadShortTermMemory(ChatState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ThreadMemoryView memoryView = conversationMemoryService.threadMemoryView(userId, threadId);
        return Map.of(
                ChatState.MEMORY_VIEW, memoryView,
                ChatState.SESSION_SUMMARY, memoryView.summary()
        );
    }

    @Override
    public Map<String, Object> loadLongTermMemory(ChatState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        return Map.of(
                ChatState.LONG_TERM_MEMORY,
                memoryContextFormatter.formatLongTermMemory(longTermMemoryRepository.listActive(userId), threadId)
        );
    }

    @Override
    public Map<String, Object> execute(ChatState state) {
        return execute(state, runEventConsumerRegistry.resolve(state.runContextKey().orElse(null)));
    }

    @Override
    public Map<String, Object> execute(ChatState state, Consumer<RunEvent> runEventConsumer) {
        PostMessageRequest request = state.<PostMessageRequest>request()
                .orElseThrow(() -> new IllegalStateException("Chat graph request is missing"));
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView()
                .orElseGet(() -> conversationMemoryService.threadMemoryView(
                        state.userId().orElseThrow(),
                        state.threadId().orElseThrow()
                ));
        chatFlowService.execute(
                state.userId().orElseThrow(),
                state.threadId().orElseThrow(),
                request,
                runEventConsumer,
                memoryView,
                state.longTermMemory().orElse("")
        );
        return Map.of(ChatState.RESULT, "completed");
    }
}
