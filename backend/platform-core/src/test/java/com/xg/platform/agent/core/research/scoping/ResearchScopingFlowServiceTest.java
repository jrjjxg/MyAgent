package com.xg.platform.agent.core.research.scoping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryMessageRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryResearchDraftRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryRunEventRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadRepository;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.contracts.message.ResearchDraftRecord;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.graph.ResearchScopingState;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.ResearchDraftRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchScopingFlowServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void createsReadyDraftPersistsMessagesAndPublishesEvents() {
        Harness harness = createHarness(tempDir, new StubScopingProvider(true));
        Map<String, Object> state = new HashMap<>(Map.of(
                ResearchScopingState.USER_ID, "user-1",
                ResearchScopingState.THREAD_ID, harness.threadId(),
                ResearchScopingState.REQUEST, new PostMessageRequest("Research the AI chip market", InteractionMode.DEEP_RESEARCH, "gemini"),
                ResearchScopingState.MEMORY_VIEW, harness.memoryView(),
                ResearchScopingState.SESSION_SUMMARY, harness.memoryView().summary(),
                ResearchScopingState.LONG_TERM_MEMORY, "Prefers concise reports"
        ));
        List<RunEvent> emitted = new ArrayList<>();

        state.putAll(harness.flowService().runScopingFrame(
                "user-1",
                harness.threadId(),
                new PostMessageRequest("Research the AI chip market", InteractionMode.DEEP_RESEARCH, "gemini"),
                harness.memoryView(),
                "Prefers concise reports",
                null,
                emitted::add
        ));
        state.putAll(harness.flowService().persistDraft(new ResearchScopingState(Map.copyOf(state))));
        state.putAll(harness.flowService().persistAssistantMessage(new ResearchScopingState(Map.copyOf(state)), emitted::add));
        state.putAll(harness.flowService().publishScopingEvents(new ResearchScopingState(Map.copyOf(state)), emitted::add));

        ResearchDraftRecord draft = harness.draftRepository().findActiveDraft("user-1", harness.threadId()).orElseThrow();
        List<MessageRecord> messages = harness.messageRepository().listMessages("user-1", harness.threadId());

        assertThat(draft.ready()).isTrue();
        assertThat(draft.revision()).isEqualTo(1);
        assertThat(draft.planSteps()).hasSize(3);
        assertThat(draft.lastAssistantMessageId()).isNotBlank();
        assertThat(messages).hasSize(2);
        assertThat(messages).extracting(MessageRecord::role).containsExactly(com.xg.platform.contracts.message.MessageRole.USER, com.xg.platform.contracts.message.MessageRole.ASSISTANT);
        assertThat(emitted).extracting(RunEvent::eventType).contains(
                "message.accepted",
                "message.delta",
                "research.brief.updated",
                "research.plan.preview.updated",
                "research.brief.ready",
                "message.completed"
        );
    }

    @Test
    void asksClarificationQuestionsAndIncrementsRevisionOnFollowUp() {
        Harness harness = createHarness(tempDir, new StubScopingProvider(false));
        ThreadMemoryView memoryView = harness.memoryView();

        ResearchDraftRecord firstDraft = executeScopingRun(harness, memoryView, "user-1", "Research the AI chip market");
        ResearchDraftRecord secondDraft = executeScopingRun(harness, memoryView, "user-1", "Focus more on Nvidia and AMD competition");

        assertThat(firstDraft.ready()).isFalse();
        assertThat(firstDraft.questions()).isNotEmpty();
        assertThat(secondDraft.ready()).isFalse();
        assertThat(secondDraft.revision()).isEqualTo(2);
        assertThat(secondDraft.planSummary()).contains("Nvidia");
    }

    private ResearchDraftRecord executeScopingRun(Harness harness,
                                                  ThreadMemoryView memoryView,
                                                  String userId,
                                                  String content) {
        ResearchDraftRecord currentDraft = harness.draftRepository().findActiveDraft(userId, harness.threadId()).orElse(null);
        Map<String, Object> state = new HashMap<>(Map.of(
                ResearchScopingState.USER_ID, userId,
                ResearchScopingState.THREAD_ID, harness.threadId(),
                ResearchScopingState.REQUEST, new PostMessageRequest(content, InteractionMode.DEEP_RESEARCH, "gemini"),
                ResearchScopingState.MEMORY_VIEW, memoryView,
                ResearchScopingState.SESSION_SUMMARY, memoryView.summary(),
                ResearchScopingState.LONG_TERM_MEMORY, ""
        ));
        if (currentDraft != null) {
            state.put(ResearchScopingState.CURRENT_DRAFT, currentDraft);
        }

        List<RunEvent> emitted = new ArrayList<>();
        state.putAll(harness.flowService().runScopingFrame(
                userId,
                harness.threadId(),
                new PostMessageRequest(content, InteractionMode.DEEP_RESEARCH, "gemini"),
                memoryView,
                "",
                currentDraft,
                emitted::add
        ));
        state.putAll(harness.flowService().persistDraft(new ResearchScopingState(Map.copyOf(state))));
        state.putAll(harness.flowService().persistAssistantMessage(new ResearchScopingState(Map.copyOf(state)), emitted::add));
        state.putAll(harness.flowService().publishScopingEvents(new ResearchScopingState(Map.copyOf(state)), emitted::add));

        assertThat(emitted).extracting(RunEvent::eventType).contains("research.questions.requested", "message.completed");
        return harness.draftRepository().findActiveDraft(userId, harness.threadId()).orElseThrow();
    }

    private ThreadMemoryView memoryView() {
        return new ThreadMemoryView("unused", "summary", List.of(), List.of(), null, null, null);
    }

    private static Harness createHarness(Path tempDir, AgentTurnExecutionSupport agentTurnExecutionSupport) {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadRuntimeService threadRuntimeService = new ThreadRuntimeService(new InMemoryThreadRepository());
        String threadId = threadRuntimeService.createThread("user-1", "workspace-1", "Thread").threadId();
        MessageRepository messageRepository = new InMemoryMessageRepository();
        ResearchDraftRepository researchDraftRepository = new InMemoryResearchDraftRepository();
        RunEventRepository runEventRepository = new InMemoryRunEventRepository();
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        return new Harness(
                threadId,
                messageRepository,
                researchDraftRepository,
                new ResearchScopingFlowService(
                        threadRuntimeService,
                        messageRepository,
                        researchDraftRepository,
                        runEventRepository,
                        payload -> {
                        },
                        artifactService,
                        agentTurnExecutionSupport,
                        objectMapper,
                        false
                )
        );
    }

    private record Harness(
            String threadId,
            MessageRepository messageRepository,
            ResearchDraftRepository draftRepository,
            ResearchScopingFlowService flowService
    ) {
        private ThreadMemoryView memoryView() {
            return new ThreadMemoryView(threadId, "summary", List.of(), List.of(), null, null, null);
        }
    }

    private static final class StubScopingProvider implements AgentTurnExecutionSupport {
        private final boolean ready;
        private int invocationCount;

        private StubScopingProvider(boolean ready) {
            this.ready = ready;
        }

        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String systemPrompt, String userContent) {
            invocationCount++;
            if (systemPrompt.contains("You are asking clarification questions")) {
                return """
                        {
                          "questions": ["Should I keep the report high-level, or go deeper into product and engineering details?"]
                        }
                        """;
            }
            String title = userContent.contains("Nvidia and AMD") ? "AI chip market - Nvidia vs AMD" : "AI chip market";
            String planSummary = userContent.contains("Nvidia and AMD")
                    ? "Focus the research on Nvidia and AMD competition while still covering market context."
                    : "Study the AI chip market baseline, vendor competition, and practical implications.";
            String missingDecisionTypes = ready ? "[]" : "[\"audience_depth\"]";
            return """
                    {
                      "title": "%s",
                      "objective": "Understand the AI accelerator market and compare the leading vendors.",
                      "scope": "Cover market context, Nvidia/AMD competition, and implications for buyers and builders.",
                      "outputFormat": "Research report",
                      "constraints": ["Use public sources and uploaded notes."],
                      "researchUnderstanding": "The user wants a deep research report, not a quick answer.",
                      "planSummary": "%s",
                      "planSteps": [
                        {
                          "title": "Establish the market baseline",
                          "objective": "Summarize the AI accelerator market and recent trends.",
                          "query": "AI accelerator market overview",
                          "useWeb": true,
                          "useDocuments": true,
                          "outputFocus": "Market context"
                        },
                        {
                          "title": "Compare Nvidia and AMD",
                          "objective": "Compare product strategy, positioning, and competition.",
                          "query": "Nvidia AMD AI accelerators competition",
                          "useWeb": true,
                          "useDocuments": true,
                          "outputFocus": "Vendor competition"
                        },
                        {
                          "title": "Summarize implications",
                          "objective": "Explain what the evidence means for adopters.",
                          "query": "AI accelerator buyer implications",
                          "useWeb": true,
                          "useDocuments": true,
                          "outputFocus": "Implications and open questions"
                        }
                      ],
                      "missingDecisionTypes": %s,
                      "ready": %s
                    }
                    """.formatted(title, planSummary, missingDecisionTypes, ready);
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<com.xg.platform.tools.ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }
    }
}
