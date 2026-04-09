package com.xg.platform.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.chat.ChatRouteDecision;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import com.xg.platform.agent.core.chat.ChatRouterService;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.document.DocumentStatus;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.tools.McpServerRegistry;
import com.xg.platform.tools.SkillRegistry;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolExecutionRequest;
import com.xg.platform.tools.ToolExecutionResult;
import com.xg.platform.tools.ToolGroup;
import com.xg.platform.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorTest {

    private final ChatRouterService chatRouterService = new ChatRouterService();

    @TempDir
    Path tempDir;

    @Test
    void routesToolAssistedChatForWeatherIntent() {
        ChatRouteDecision decision = chatRouterService.route(
                request("Help me check tomorrow's Tianjin weather"),
                List.of()
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.CHAT);
        assertThat(decision.workflow()).isEqualTo("chat");
        assertThat(decision.toolsEnabled()).isTrue();
    }

    @Test
    void routesGeneralChatForCasualGreeting() {
        ChatRouteDecision decision = chatRouterService.route(
                request("good morning, gemini"),
                List.of()
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.CHAT);
        assertThat(decision.workflow()).isEqualTo("chat");
        assertThat(decision.toolsEnabled()).isTrue();
    }

    @Test
    void forcesDocumentQaWhenExplicitDocumentIdsAreProvided() {
        ChatRouteDecision decision = chatRouterService.route(
                requestWithSelectedDocuments("thread-1", "What does this document conclude?", List.of("doc-1")),
                List.of(document())
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.DOCUMENT_QA);
        assertThat(decision.workflow()).isEqualTo("document-qa");
        assertThat(decision.toolsEnabled()).isTrue();
    }

    @Test
    void treatsExplicitUrlAsToolAssistedIntent() {
        ChatRouteDecision decision = chatRouterService.route(
                request("Please inspect https://example.com for me"),
                List.of()
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.CHAT);
    }

    @Test
    void routesDocumentQaWhenThreadHasDocumentsAndUserAsksAboutDocument() {
        ChatRouteDecision decision = chatRouterService.route(
                request("Summarize the main conclusion of this paper"),
                List.of(document())
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.DOCUMENT_QA);
        assertThat(decision.workflow()).isEqualTo("document-qa");
        assertThat(decision.toolsEnabled()).isTrue();
    }

    @Test
    void keepsAmbiguousRealtimeQuestionInChatWithoutStandaloneModelRouting() {
        ChatRouteDecision decision = chatRouterService.route(
                request("Is it a good time to go out in Tianjin right now?"),
                List.of()
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.CHAT);
        assertThat(decision.toolsEnabled()).isTrue();
    }

    @Test
    void keepsGeneralOpinionQuestionsInChatWhenNoDocumentSignalExists() {
        ChatRouteDecision decision = chatRouterService.route(
                request("Do you think this direction is worth pursuing?"),
                List.of()
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.CHAT);
        assertThat(decision.toolsEnabled()).isTrue();
    }

    @Test
    void routesToolAssistedChatForReadableChineseWeatherIntent() {
        ChatRouteDecision decision = chatRouterService.route(
                request("\u5e2e\u6211\u67e5\u8be2\u4e0b\u5929\u6d25\u660e\u5929\u7684\u5929\u6c14"),
                List.of()
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.CHAT);
        assertThat(decision.workflow()).isEqualTo("chat");
        assertThat(decision.toolsEnabled()).isTrue();
    }

    @Test
    void routesGeneralChatForReadableChineseGreeting() {
        ChatRouteDecision decision = chatRouterService.route(
                request("\u65e9\u4e0a\u597d\uff0cgemini"),
                List.of()
        );

        assertThat(decision.routeKind()).isEqualTo(ChatRouteKind.CHAT);
        assertThat(decision.workflow()).isEqualTo("chat");
        assertThat(decision.toolsEnabled()).isTrue();
    }

    @Test
    void executesToolAssistedChatWithoutWeatherFastPath() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        SkillRegistry skillRegistry = new SkillRegistry(
                tempDir.resolve("skills"),
                new McpServerRegistry(writeExtensionsConfig(), objectMapper)
        );
        ThreadRuntimeService threadRuntimeService = new ThreadRuntimeService(new InMemoryRuntimeSupport.InMemoryThreadRepository());
        String threadId = threadRuntimeService.createThread("user-1", "workspace-1", "Thread").threadId();
        DocumentStore documentStore = new DocumentStore(workspaceManager, threadRuntimeService, objectMapper);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                promptRequest -> "",
                skillRegistry,
                new StubWeatherFailingToolService(objectMapper),
                chatRouterService,
                new StubAgentTurnExecutionSupport("Tool-assisted fallback response"),
                documentStore,
                null,
                null,
                null,
                null,
                workspaceManager,
                threadRuntimeService,
                null,
                objectMapper,
                false,
                false,
                false,
                4,
                2,
                2,
                1,
                30000L
        );

        AgentExecutionResult result = orchestrator.execute(
                AgentMode.GENERAL,
                request(threadId, "\u5e2e\u6211\u67e5\u4e0b\u5929\u6d25\u5e02\u4eca\u5929\u7684\u5929\u6c14", ChatRouteKind.CHAT),
                new AgentOutputEmitter() {
                    @Override
                    public void emitText(String delta) {
                    }
                }
        );

        assertThat(result.routeKind()).isEqualTo(ChatRouteKind.CHAT);
        assertThat(result.workflow()).isEqualTo("chat");
        assertThat(result.toolsEnabled()).isTrue();
        assertThat(result.finalContent()).isEqualTo("Tool-assisted fallback response");
    }

    private AgentExecutionRequest request(String threadId, String message) {
        return new AgentExecutionRequest(
                "user-1",
                threadId,
                "run-1",
                message,
                null,
                "gemini",
                List.of(),
                List.of(),
                "auto",
                List.of(),
                List.of(),
                List.of(),
                "summary",
                "",
                null
        );
    }

    private AgentExecutionRequest request(String message) {
        return request("thread-1", message);
    }

    private AgentExecutionRequest request(String threadId, String message, ChatRouteKind routeKind) {
        return new AgentExecutionRequest(
                "user-1",
                threadId,
                "run-1",
                message,
                null,
                "gemini",
                List.of(),
                List.of(),
                "auto",
                List.of(),
                List.of(),
                List.of(),
                "summary",
                "",
                routeKind
        );
    }

    private AgentExecutionRequest requestWithSelectedDocuments(String threadId, String message, List<String> selectedDocumentIds) {
        return new AgentExecutionRequest(
                "user-1",
                threadId,
                "run-1",
                message,
                null,
                "gemini",
                List.of(),
                List.of(),
                "auto",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "summary",
                "",
                null,
                null,
                null,
                List.of(),
                selectedDocumentIds
        );
    }

    private DocumentRecord document() {
        Instant now = Instant.now();
        return new DocumentRecord(
                "doc-1",
                "workspace-1",
                "thread-1",
                "artifact-1",
                "paper.pdf",
                DocumentStatus.READY,
                "primary-1",
                "chunks-1",
                now,
                now
        );
    }

    private Path writeExtensionsConfig() throws Exception {
        Path configPath = tempDir.resolve("extensions.json");
        Files.writeString(configPath, """
                {
                  "mcpServers": {}
                }
                """);
        return configPath;
    }

    private static final class StubAgentTurnExecutionSupport implements AgentTurnExecutionSupport {

        private final String response;

        private StubAgentTurnExecutionSupport(String response) {
            this.response = response;
        }

        @Override
        public String resolveProviderId(String requestedProviderId) {
            return requestedProviderId == null || requestedProviderId.isBlank() ? "gemini" : requestedProviderId;
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            return response;
        }

        @Override
        public String runModelLoop(String providerId, AgentExecutionRequest request, String prompt, List<ToolDescriptor> availableTools, AgentOutputEmitter outputEmitter) {
            return response;
        }
    }

    private static final class StubWeatherFailingToolService implements AgentToolService {

        private final ToolDescriptor weatherTool;

        private StubWeatherFailingToolService(ObjectMapper objectMapper) {
            this.weatherTool = new ToolDescriptor(
                    "weather",
                    "Weather lookup",
                    objectMapper.createObjectNode(),
                    ToolGroup.SEARCH,
                    "builtin"
            );
        }

        @Override
        public List<ToolDescriptor> listAvailableTools(String userId) {
            return List.of(weatherTool);
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            throw new IllegalStateException("weather failed with status 500");
        }
    }
}
