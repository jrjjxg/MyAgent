package com.xg.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.PlatformApiApplication;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolExecutionRequest;
import com.xg.platform.tools.ToolExecutionResult;
import com.xg.platform.tools.ToolGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PlatformApiApplication.class)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "platform.test.postgres.enabled", matches = "true")
class AgentFlowIntegrationTest {

    private static final Path DATA_ROOT = createDataRoot();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", LocalPostgresIntegrationSupport::jdbcUrl);
        registry.add("spring.datasource.username", LocalPostgresIntegrationSupport::username);
        registry.add("spring.datasource.password", LocalPostgresIntegrationSupport::password);
        registry.add("platform.data-root", () -> DATA_ROOT.toString().replace('\\', '/'));
        registry.add("platform.dev-user-id", () -> "integration-user");
        registry.add("platform.ai.gemini.model", () -> "gemini-test-model");
        registry.add("platform.model.default-provider", () -> "gemini");
        registry.add("platform.skills-root", () -> Path.of("..", "..", "skills").normalize().toString().replace('\\', '/'));
        registry.add("platform.extensions-config-path", () -> Path.of("..", "..", "extensions.json").normalize().toString().replace('\\', '/'));
        registry.add("platform.tools.document-script", () -> Path.of("..", "..", "tools", "document_tools.py").normalize().toString().replace('\\', '/'));
    }

    @Test
    void createsThreadUploadsFileExecutesMessageAndExposesArtifactsAndTasks() throws Exception {
        String userId = "user-a";
        String threadId = createThread(userId, "Integration Thread");

        mockMvc.perform(multipart("/threads/{threadId}/uploads", threadId)
                        .file(new MockMultipartFile(
                                "file",
                                "notes.txt",
                                MediaType.TEXT_PLAIN_VALUE,
                                "hello docs".getBytes(StandardCharsets.UTF_8)
                        ))
                        .header("X-User-Id", userId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artifact.type").value("UPLOAD"))
                .andExpect(jsonPath("$.artifact.name").value("notes.txt"))
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.ingestTaskId").isNotEmpty());

        MvcResult asyncResult = mockMvc.perform(post("/threads/{threadId}/messages", threadId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "content": "Summarize the uploaded file",
                                  "interactionMode": "CHAT",
                                  "providerId": "gemini"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncResult.getAsyncResult(5000);

        MvcResult streamResult = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        String streamBody = streamResult.getResponse().getContentAsString();
        assertThat(streamBody).contains("\"eventType\":\"run.started\"");
        assertThat(streamBody).contains("\"eventType\":\"agent.selected\"");
        assertThat(streamBody).contains("\"eventType\":\"message.delta\"");
        assertThat(streamBody).contains("\"eventType\":\"run.completed\"");
        assertThat(streamBody).contains("\"providerId\":\"gemini\"");

        mockMvc.perform(get("/threads/{threadId}/messages", threadId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));

        MvcResult artifactsResult = mockMvc.perform(get("/threads/{threadId}/artifacts", threadId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode artifacts = objectMapper.readTree(artifactsResult.getResponse().getContentAsString());
        assertThat(artifacts.toString()).contains("UPLOAD");

        mockMvc.perform(get("/threads/{threadId}/documents", threadId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").isNotEmpty());
    }

    @Test
    void deepResearchProducesEditablePlanAndRunsTaskWithProgressEvents() throws Exception {
        String userId = "user-dr";
        String threadId = createThread(userId, "Deep Research Thread");

        mockMvc.perform(multipart("/threads/{threadId}/uploads", threadId)
                        .file(new MockMultipartFile(
                                "file",
                                "market-notes.txt",
                                MediaType.TEXT_PLAIN_VALUE,
                                "Internal notes about Nvidia and AMD competition.".getBytes(StandardCharsets.UTF_8)
                        ))
                        .header("X-User-Id", userId))
                .andExpect(status().isCreated());

        streamDeepResearchMessage(userId, threadId, "Research the AI chip market");

        mockMvc.perform(get("/threads/{threadId}/research-draft", threadId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.planSummary").isNotEmpty())
                .andExpect(jsonPath("$.planSteps[0].title").isNotEmpty());

        streamDeepResearchMessage(userId, threadId, "Focus more on Nvidia and AMD competition");

        mockMvc.perform(get("/threads/{threadId}/research-draft", threadId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.planSummary").value(org.hamcrest.Matchers.containsString("Nvidia")));

        mockMvc.perform(post("/threads/{threadId}/research-draft/start", threadId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "gemini",
                                  "draftRevision": 1
                                }
                                """))
                .andExpect(status().isBadRequest());

        MvcResult startResult = mockMvc.perform(post("/threads/{threadId}/research-draft/start", threadId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId": "gemini",
                                  "draftRevision": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("RESEARCH"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString()).path("taskId").asText();
        awaitTaskCompletion(userId, threadId, taskId);

        mockMvc.perform(get("/threads/{threadId}/tasks/{taskId}", threadId, taskId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.stage").value("completed"))
                .andExpect(jsonPath("$.resultArtifactId").isNotEmpty());

        MvcResult eventsResult = mockMvc.perform(get("/threads/{threadId}/events?limit=120", threadId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode events = objectMapper.readTree(eventsResult.getResponse().getContentAsString());
        String serializedEvents = events.toString();
        assertThat(serializedEvents).contains("research.plan.approved");
        assertThat(serializedEvents).contains("research.step.started");
        assertThat(serializedEvents).contains("research.site.discovered");
        assertThat(serializedEvents).contains("research.activity");
        assertThat(serializedEvents).contains("\"kind\":\"reflection\"");
        assertThat(serializedEvents).contains("\"sourceId\":");
        assertThat(serializedEvents).contains("research.report.ready");
        assertThat(serializedEvents).contains("task.completed");

        MvcResult artifactsResult = mockMvc.perform(get("/threads/{threadId}/artifacts", threadId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode artifacts = objectMapper.readTree(artifactsResult.getResponse().getContentAsString());
        assertThat(artifacts.toString()).contains("REPORT");
        assertThat(artifacts.toString()).contains("UPLOAD");
    }

    @Test
    void rejectsUnknownProviderBeforeStartingSseExecution() throws Exception {
        String threadId = createThread("user-b", "Bad Provider Thread");

        mockMvc.perform(post("/threads/{threadId}/messages", threadId)
                        .header("X-User-Id", "user-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "hello",
                                  "interactionMode": "CHAT",
                                  "providerId": "missing-provider"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown provider: missing-provider"));
    }

    @Test
    void exposesThreadMemoryAndExplicitLongTermMemoryApis() throws Exception {
        String userId = "user-memory";
        String threadId = createThread(userId, "Memory Thread");

        MvcResult asyncResult = mockMvc.perform(post("/threads/{threadId}/messages", threadId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "content": "Remember that I prefer concise bullet summaries",
                                  "interactionMode": "CHAT",
                                  "providerId": "gemini"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();
        asyncResult.getAsyncResult(5000);
        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk());

        mockMvc.perform(get("/threads/{threadId}/memory", threadId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value(threadId))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.recentMessages[0].role").value("USER"))
                .andExpect(jsonPath("$.recentMessages[1].role").value("ASSISTANT"));

        mockMvc.perform(post("/memory/long-term")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Output style",
                                  "content": "Use concise bullet summaries when possible",
                                  "sourceThreadId": "%s"
                                }
                                """.formatted(threadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memoryId").isNotEmpty());

        mockMvc.perform(get("/memory/long-term").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Output style"));
    }

    @Test
    void exposesStructuredProfileAndStableFactApis() throws Exception {
        String userId = "user-structured-memory";
        String threadId = createThread(userId, "Structured Memory Thread");

        mockMvc.perform(get("/memory/profile").header("X-User-Id", userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/memory/profile")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Alex",
                                  "preferredLanguage": "zh-CN",
                                  "preferredOutputStyles": ["concise", "bullet"],
                                  "projectTags": ["platform", "memory"],
                                  "notes": "Keep answers short and implementation-focused."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.preferredLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.preferredOutputStyles[0]").value("concise"))
                .andExpect(jsonPath("$.projectTags[0]").value("platform"))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("Display name: Alex")));

        mockMvc.perform(get("/memory/profile").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("implementation-focused")));

        MvcResult factResult = mockMvc.perform(post("/memory/facts")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "factType": "preference",
                                  "title": "Output style",
                                  "content": "Use concise bullet summaries",
                                  "sourceThreadId": "%s",
                                  "sourceTaskId": "task-1"
                                }
                                """.formatted(threadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factType").value("preference"))
                .andExpect(jsonPath("$.category").value("preference"))
                .andExpect(jsonPath("$.fact").value("Use concise bullet summaries"))
                .andExpect(jsonPath("$.sourceTaskId").value("task-1"))
                .andReturn();

        String memoryId = objectMapper.readTree(factResult.getResponse().getContentAsString()).path("memoryId").asText();

        mockMvc.perform(get("/memory/facts").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memoryId").value(memoryId))
                .andExpect(jsonPath("$[0].title").value("Output style"))
                .andExpect(jsonPath("$[0].content").value("Use concise bullet summaries"))
                .andExpect(jsonPath("$[0].sourceThreadId").value(threadId))
                .andExpect(jsonPath("$[0].sourceTaskId").value("task-1"));

        mockMvc.perform(put("/memory/facts/{memoryId}", memoryId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "factType": "preference",
                                  "title": "Output style",
                                  "content": "Prefer short implementation notes",
                                  "sourceThreadId": "%s",
                                  "sourceTaskId": "task-2"
                                }
                                """.formatted(threadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Prefer short implementation notes"))
                .andExpect(jsonPath("$.fact").value("Prefer short implementation notes"))
                .andExpect(jsonPath("$.sourceTaskId").value("task-2"));

        mockMvc.perform(delete("/memory/facts/{memoryId}", memoryId).header("X-User-Id", userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/memory/facts").header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void streamsRunFailedInsteadOfEscalatingSseErrors() throws Exception {
        String userId = "user-c";
        String threadId = createThread(userId, "Failing Run Thread");

        MvcResult asyncResult = mockMvc.perform(post("/threads/{threadId}/messages", threadId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "content": "Please trigger model failure",
                                  "interactionMode": "CHAT",
                                  "providerId": "gemini"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncResult.getAsyncResult(5000);

        MvcResult streamResult = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        String streamBody = streamResult.getResponse().getContentAsString();
        assertThat(streamBody).contains("\"eventType\":\"run.started\"");
        assertThat(streamBody).contains("\"eventType\":\"run.failed\"");
        assertThat(streamBody).contains("Simulated model failure");
    }

    private void streamDeepResearchMessage(String userId, String threadId, String content) throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/threads/{threadId}/messages", threadId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {
                                  "content": "%s",
                                  "interactionMode": "DEEP_RESEARCH",
                                  "providerId": "gemini"
                                }
                                """.formatted(content)))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncResult.getAsyncResult(5000);
        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    private void awaitTaskCompletion(String userId, String threadId, String taskId) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/threads/{threadId}/tasks/{taskId}", threadId, taskId).header("X-User-Id", userId))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode task = objectMapper.readTree(result.getResponse().getContentAsString());
            String status = task.path("status").asText();
            if ("COMPLETED".equals(status)) {
                return;
            }
            if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                throw new AssertionError("Research task finished unexpectedly with status " + status);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for research task completion");
    }

    private String createThread(String userId, String title) throws Exception {
        MvcResult workspaceResult = mockMvc.perform(post("/workspaces")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s Workspace"
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspaceId").isNotEmpty())
                .andReturn();
        JsonNode workspaceJson = objectMapper.readTree(workspaceResult.getResponse().getContentAsString());
        String workspaceId = workspaceJson.get("workspaceId").asText();

        MvcResult result = mockMvc.perform(post("/threads")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": "%s",
                                  "title": "%s"
                                }
                                """.formatted(workspaceId, title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.threadId").isNotEmpty())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("threadId").asText();
    }

    @TestConfiguration
    static class TestDocsModelConfiguration {

        @Bean
        @Primary
        AgentTurnExecutionSupport testAgentTurnExecutionSupport() {
            return new StubAgentTurnExecutionSupport();
        }

        @Bean
        @Primary
        AgentToolService stubToolExecutor(ObjectMapper objectMapper) {
            return new StubToolService(objectMapper);
        }

        private static final class StubAgentTurnExecutionSupport implements AgentTurnExecutionSupport {
            @Override
            public String resolveProviderId(String requestedProviderId) {
                String providerId = requestedProviderId == null || requestedProviderId.isBlank()
                        ? "gemini"
                        : requestedProviderId;
                if (!"gemini".equals(providerId)) {
                    throw new IllegalArgumentException("Unknown provider: " + providerId);
                }
                return providerId;
            }

            @Override
            public String runTextTurn(String providerId, String modelOverride, String systemPrompt, String userContent) {
                if (userContent != null && userContent.contains("Please trigger model failure")) {
                    throw new IllegalStateException("Simulated model failure");
                }

                if (systemPrompt.contains("You are preparing a deep research brief")) {
                    boolean revised = userContent.contains("Nvidia and AMD");
                    String planSummary = revised
                            ? "Focus the research on Nvidia and AMD competition, while still covering market context and implications."
                            : "Study the AI chip market baseline, vendor competition, and practical implications.";
                    String title = revised ? "AI chip market - Nvidia vs AMD" : "AI chip market";
                    String json = """
                            {
                              "title": "%s",
                              "objective": "Understand the AI accelerator market and compare the leading vendors.",
                              "scope": "Cover market context, Nvidia/AMD competition, and implications for buyers and builders.",
                              "outputFormat": "Research report",
                              "constraints": ["Use public sources and uploaded notes."],
                              "researchUnderstanding": "The user wants a Gemini-style deep research report, not a quick answer.",
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
                              "missingDecisionTypes": [],
                              "ready": true
                            }
                            """.formatted(title, planSummary);
                    return json;
                }

                if (systemPrompt.contains("You are asking clarification questions")) {
                    return """
                            {
                              "questions": ["Should I keep the report high-level, or go deeper into product and engineering details?"]
                            }
                            """;
                }

                if (systemPrompt.contains("You are a focused deep research unit")) {
                    return """
                            {
                              "notes": "The evidence shows strong competition in accelerators, with clear differences in positioning and ecosystem.",
                              "localConclusion": "Nvidia leads ecosystem strength while AMD is emphasized as a competitive challenger.",
                              "sources": ["Example source - https://example.com/chips"]
                            }
                            """;
                }

                if (systemPrompt.contains("You are compressing deep research unit outputs into final findings")) {
                    return """
                            {
                              "findings": [
                                {
                                  "heading": "Market growth remains the baseline context",
                                  "summary": "The category remains fast-moving and ecosystem-driven.",
                                  "evidenceStrength": "medium",
                                  "supportingSources": ["Example source - https://example.com/chips"]
                                },
                                {
                                  "heading": "Nvidia and AMD competition is central",
                                  "summary": "The competitive dynamic is a primary lens for the report.",
                                  "evidenceStrength": "medium",
                                  "supportingSources": ["Example source - https://example.com/chips"]
                                }
                              ]
                            }
                            """;
                }

                if (systemPrompt.contains("You are writing the final report for a deep research task")) {
                    return """
                            # Executive Summary

                            The AI accelerator market is growing quickly, and Nvidia versus AMD is the most important competitive thread in this run.

                            ## Key Findings

                            - Nvidia remains strong on ecosystem depth and platform maturity.
                            - AMD appears as the clearest challenger in the reviewed evidence.

                            ## Evidence and Caveats

                            The report combines uploaded notes with web evidence. Some claims remain directional and should be checked against fresh product releases.

                            ## Conclusion

                            Buyers should treat ecosystem maturity and software support as seriously as raw hardware performance.
                            """;
                }

                return """
                        # Response

                        Summarized docs request with [notes.txt, p.1].
                        """;
            }

            @Override
            public String runModelLoop(String providerId,
                                       AgentExecutionRequest request,
                                       String prompt,
                                       java.util.List<com.xg.platform.tools.ToolDescriptor> availableTools,
                                       AgentOutputEmitter outputEmitter) {
                return runTextTurn(providerId, null, prompt, request.message());
            }
        }

        private static final class StubToolService implements AgentToolService {
            private final ObjectMapper objectMapper;

            private StubToolService(ObjectMapper objectMapper) {
                this.objectMapper = objectMapper;
            }

            @Override
            public java.util.List<ToolDescriptor> listAvailableTools(String userId) {
                return java.util.List.of(
                        tool("web_search", ToolGroup.SEARCH),
                        tool("web_fetch", ToolGroup.SEARCH),
                        tool("ask_clarification", ToolGroup.WORKSPACE),
                        tool("research_reflect", ToolGroup.WORKSPACE),
                        tool("load_skill", ToolGroup.WORKSPACE)
                );
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                ObjectNode output = objectMapper.createObjectNode();
                if ("web_search".equals(request.tool().name())) {
                    ArrayNode results = output.putArray("results");
                    results.addObject()
                            .put("title", "AI chip market overview")
                            .put("url", "https://example.com/chips")
                            .put("snippet", "Overview of the AI accelerator market and vendor competition.");
                    results.addObject()
                            .put("title", "Nvidia and AMD competition")
                            .put("url", "https://example.com/vendors")
                            .put("snippet", "Notes on Nvidia and AMD strategy in accelerators.");
                    return new ToolExecutionResult("web_search", output, false, "ok");
                }
                if ("web_fetch".equals(request.tool().name())) {
                    output.put("title", "Fetched source");
                    output.put("text", "Fetched page text describing the AI accelerator market and Nvidia versus AMD positioning.");
                    return new ToolExecutionResult("web_fetch", output, false, "ok");
                }
                if ("ask_clarification".equals(request.tool().name())) {
                    output.put("status", "not-needed");
                    return new ToolExecutionResult("ask_clarification", output, false, "ok");
                }
                if ("research_reflect".equals(request.tool().name())) {
                    output.put("status", "reflected");
                    output.put("summary", "Coverage is developing but one more focused source would strengthen the Nvidia and AMD comparison.");
                    output.put("coverage", "developing");
                    output.put("confidence", "medium");
                    output.put("needsMoreEvidence", true);
                    ArrayNode focusAreas = output.putArray("focusAreas");
                    focusAreas.add("Nvidia and AMD competition");
                    ArrayNode nextActions = output.putArray("nextActions");
                    nextActions.add("Gather one more focused source on Nvidia and AMD competition.");
                    ArrayNode missingEvidence = output.putArray("missingEvidence");
                    missingEvidence.add("Need another corroborating source for the vendor comparison.");
                    return new ToolExecutionResult("research_reflect", output, false, "ok");
                }
                return new ToolExecutionResult(request.tool().name(), output, false, "ok");
            }

            private ToolDescriptor tool(String name, ToolGroup group) {
                return new ToolDescriptor(name, name, objectMapper.createObjectNode(), group, "builtin");
            }
        }
    }

    private static Path createDataRoot() {
        try {
            Path path = Path.of("target", "test-data", UUID.randomUUID().toString());
            Files.createDirectories(path);
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create test data root", exception);
        }
    }
}
