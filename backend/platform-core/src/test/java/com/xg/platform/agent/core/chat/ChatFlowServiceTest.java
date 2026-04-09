package com.xg.platform.agent.core.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentExecutionResult;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.ConversationResponder;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryMessageRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryRunEventRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadRepository;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatFlowServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void executesResponderPersistsEventsAndKeepsReportArtifactsAbsent() {
        ChatHarness harness = createHarness(tempDir, new StubStreamingConversationResponder(), false);
        List<RunEvent> emitted = new ArrayList<>();

        harness.chatFlowService().execute(
                "user-1",
                harness.threadId(),
                new PostMessageRequest("Analyze this thread", InteractionMode.CHAT, "gemini"),
                emitted::add,
                harness.memoryView(),
                ""
        );

        List<MessageRecord> messages = harness.messageRepository().listMessages("user-1", harness.threadId());
        assertThat(emitted).extracting(RunEvent::eventType).containsExactly(
                "message.accepted",
                "run.started",
                "agent.selected",
                "route.selected",
                "message.delta",
                "message.delta",
                "message.completed",
                "run.completed"
        );
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).interactionMode()).isEqualTo(InteractionMode.CHAT);
        assertThat(messages.get(1).role().name()).isEqualTo("ASSISTANT");
        assertThat(harness.artifactService().listArtifacts("user-1", harness.threadId()))
                .extracting(artifact -> artifact.type())
                .doesNotContain(ArtifactType.REPORT);
    }

    @Test
    void publishesFailureEventsAndRethrowsWhenResponderFails() {
        ChatHarness harness = createHarness(tempDir, new StubFailingConversationResponder(), false);
        List<RunEvent> emitted = new ArrayList<>();

        assertThatThrownBy(() -> harness.chatFlowService().execute(
                "user-1",
                harness.threadId(),
                new PostMessageRequest("Fail this run", InteractionMode.CHAT, "gemini"),
                emitted::add,
                harness.memoryView(),
                ""
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated model failure");

        assertThat(emitted).extracting(RunEvent::eventType).containsExactly(
                "message.accepted",
                "run.started",
                "agent.selected",
                "route.selected",
                "message.failed",
                "run.failed"
        );
    }

    private record ChatHarness(
            String threadId,
            MessageRepository messageRepository,
            ArtifactService artifactService,
            ChatFlowService chatFlowService
    ) {
        private ThreadMemoryView memoryView() {
            return new ThreadMemoryView(threadId, "No conversation memory yet.", List.of(), List.of(), null, null, null);
        }
    }

    private static ChatHarness createHarness(Path tempDir, ConversationResponder conversationResponder, boolean logAgentFlow) {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadRuntimeService threadRuntimeService = new ThreadRuntimeService(new InMemoryThreadRepository());
        String threadId = threadRuntimeService.createThread("user-1", "workspace-1", "Thread").threadId();
        MessageRepository messageRepository = new InMemoryMessageRepository();
        RunEventRepository runEventRepository = new InMemoryRunEventRepository();
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        DocumentStore documentStore = new DocumentStore(workspaceManager, threadRuntimeService, objectMapper);
        ChatFlowService chatFlowService = new ChatFlowService(
                threadRuntimeService,
                messageRepository,
                runEventRepository,
                payload -> {
                },
                artifactService,
                workspaceManager,
                documentStore,
                new ChatRouterService(),
                conversationResponder,
                new StubAgentTurnExecutionSupport(),
                logAgentFlow
        );
        return new ChatHarness(threadId, messageRepository, artifactService, chatFlowService);
    }

    private static final class StubStreamingConversationResponder implements ConversationResponder {
        @Override
        public AgentExecutionResult respond(AgentExecutionRequest request, AgentOutputEmitter outputEmitter) {
            outputEmitter.emitText("hello ");
            outputEmitter.emitText("world");
            return new AgentExecutionResult("general-agent", request.providerId(), null, "done", "hello world", true);
        }
    }

    private static final class StubFailingConversationResponder implements ConversationResponder {
        @Override
        public AgentExecutionResult respond(AgentExecutionRequest request, AgentOutputEmitter outputEmitter) {
            throw new IllegalStateException("Simulated model failure");
        }
    }

    private static final class StubAgentTurnExecutionSupport implements AgentTurnExecutionSupport {
        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            throw new UnsupportedOperationException();
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
