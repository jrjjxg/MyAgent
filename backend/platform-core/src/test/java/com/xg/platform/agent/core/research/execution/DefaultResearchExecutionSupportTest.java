package com.xg.platform.agent.core.research.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.ResearchUnit;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.tooling.application.McpServerRegistry;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;
import com.xg.platform.tooling.domain.ToolGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultResearchExecutionSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void executesResearchUnitThroughAgentLoopWithBoundedResearchTools() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        RecordingEmitter outputEmitter = new RecordingEmitter();
        StubAgentTurnExecutionSupport turnExecutionSupport = new StubAgentTurnExecutionSupport();
        DefaultResearchExecutionSupport support = new DefaultResearchExecutionSupport(
                emptySkillRegistry(objectMapper),
                new StaticResearchToolService(objectMapper),
                null,
                null,
                null,
                null,
                turnExecutionSupport,
                objectMapper,
                false,
                false,
                DefaultResearchExecutionSupport.Limits.defaults()
        );

        var result = support.executeResearchUnit(
                "gemini",
                AgentExecutionRequest.builder()
                        .userId("user-1")
                        .threadId("thread-1")
                        .runId("run-1")
                        .message("research topic")
                        .providerId("gemini")
                        .requestedCapabilities(List.of())
                        .skillIds(List.of())
                        .skillSelectionMode("auto")
                        .artifacts(List.of())
                        .uploadedFiles(List.of())
                        .recentMessages(List.of())
                        .sessionSummary("")
                        .longTermMemory("")
                        .build(),
                "Research topic",
                List.of("Focus on grounded evidence"),
                List.of(),
                new ResearchUnit("unit-1", "Blocked source handling", "Handle blocked web pages", "blocked query", false, true, "Produce findings"),
                outputEmitter,
                1,
                1
        );

        assertThat(result.notes()).contains("Used the bounded unit-agent loop.");
        assertThat(result.notes()).contains("verified web coverage stayed below the required threshold");
        assertThat(result.localConclusion()).contains("The research unit completed through the autonomous model loop.");
        assertThat(turnExecutionSupport.lastAvailableToolNames())
                .containsExactly(
                        "web_search",
                        "web_fetch",
                        "research_reflect",
                        "load_skill",
                        "load_skill_resource",
                        "run_skill_command",
                        "skill_process_status",
                        "stop_skill_process"
                );
        assertThat(turnExecutionSupport.lastPrompt()).contains("You are a focused deep research unit agent.");
        assertThat(turnExecutionSupport.lastPrompt()).contains("Available skills:");
        assertThat(turnExecutionSupport.lastPrompt()).contains("Call load_skill before following any skill workflow.");
        assertThat(outputEmitter.activitySummaries())
                .anyMatch(summary -> summary.contains("Synthesizing the evidence collected"));
    }

    private SkillRegistry emptySkillRegistry(ObjectMapper objectMapper) throws Exception {
        Path configPath = tempDir.resolve("extensions.json");
        Files.writeString(configPath, """
                {
                  "mcpServers": {
                    "search": {
                      "enabled": true,
                      "toolGroups": ["search"]
                    }
                  }
                }
                """);
        Path skillsRoot = tempDir.resolve("skills");
        Files.createDirectories(skillsRoot);
        return new SkillRegistry(skillsRoot, new McpServerRegistry(configPath, objectMapper));
    }

    private static final class StaticResearchToolService implements AgentToolService {

        private final ObjectMapper objectMapper;

        private StaticResearchToolService(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public List<ToolDescriptor> listAvailableTools(String userId) {
            return List.of(
                    tool("web_search"),
                    tool("web_fetch"),
                    tool("research_reflect"),
                    tool("load_skill"),
                    tool("load_skill_resource"),
                    tool("run_skill_command"),
                    tool("skill_process_status"),
                    tool("stop_skill_process")
            );
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            return new ToolExecutionResult(
                    request.tool().name(),
                    objectMapper.createObjectNode().put("status", "ok"),
                    false,
                    "ok"
            );
        }

        @Override
        public ToolDescriptor requireTool(String userId, String toolName) {
            return listAvailableTools(userId).stream()
                    .filter(tool -> tool.name().equals(toolName))
                    .findFirst()
                    .orElseThrow();
        }

        private ToolDescriptor tool(String name) {
            ToolGroup group = switch (name) {
                case "web_search", "web_fetch" -> ToolGroup.SEARCH;
                default -> ToolGroup.WORKSPACE;
            };
            return new ToolDescriptor(name, name, objectMapper.createObjectNode(), group, "builtin");
        }
    }

    private static final class StubAgentTurnExecutionSupport implements AgentTurnExecutionSupport {

        private List<String> lastAvailableToolNames = List.of();
        private String lastPrompt = "";

        @Override
        public String resolveProviderId(String requestedProviderId) {
            return requestedProviderId == null ? "gemini" : requestedProviderId;
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            return """
                    {
                      "notes": "Fallback text turn.",
                      "localConclusion": "Fallback text conclusion.",
                      "sources": []
                    }
                    """;
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            this.lastPrompt = prompt;
            this.lastAvailableToolNames = availableTools.stream().map(ToolDescriptor::name).toList();
            return """
                    {
                      "notes": "Used the bounded unit-agent loop.",
                      "localConclusion": "The research unit completed through the autonomous model loop.",
                      "sources": ["web_search", "web_fetch"]
                    }
                    """;
        }

        private List<String> lastAvailableToolNames() {
            return lastAvailableToolNames;
        }

        private String lastPrompt() {
            return lastPrompt;
        }
    }

    private static final class RecordingEmitter implements AgentOutputEmitter {

        private final List<String> activitySummaries = new ArrayList<>();

        @Override
        public void emitText(String delta) {
        }

        @Override
        public void emitEvent(RunEventType eventType, Object payload) {
            if (eventType != RunEventType.RESEARCH_ACTIVITY || !(payload instanceof Map<?, ?> map)) {
                return;
            }
            Object summary = map.get("summary");
            if (summary instanceof String value) {
                activitySummaries.add(value);
            }
        }

        private List<String> activitySummaries() {
            return activitySummaries;
        }
    }
}
