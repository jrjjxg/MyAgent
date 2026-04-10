package com.xg.platform.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.tooling.application.BuiltinToolExecutor;
import com.xg.platform.tooling.application.BuiltinWeatherClient;
import com.xg.platform.tooling.application.McpServerRegistry;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;
import com.xg.platform.tooling.domain.ToolGroup;

class BuiltinToolExecutorTest {

    private static final String USER_ID = "user-1";
    private static final String THREAD_ID = "thread-1";
    private static final String RUN_ID = "run-1";

    @TempDir
    Path tempDir;

    @Test
    void researchReflectRequestsMoreEvidenceWhenCoverageIsThin() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        BuiltinToolExecutor executor = new BuiltinToolExecutor(
                new WorkspaceManager(tempDir),
                new SkillRegistry(tempDir.resolve("skills"), new McpServerRegistry(writeExtensionsConfig(), objectMapper)),
                null,
                new BuiltinWeatherClient(objectMapper, Duration.ofSeconds(5)),
                objectMapper
        );

        JsonNode arguments = objectMapper.createObjectNode()
                .put("topic", "AI chip competition")
                .put("query", "Nvidia AMD AI accelerators")
                .put("evidenceSummary", "Two snippets mention competition, but the coverage is still narrow.")
                .put("sourceCount", 1)
                .put("stepIndex", 1)
                .put("totalSteps", 3)
                .put("focus", "Vendor positioning and ecosystem tradeoffs");

        ToolExecutionResult result = executor.execute(ToolExecutionRequest.builder()
                .userId(USER_ID)
                .threadId(THREAD_ID)
                .runId(RUN_ID)
                .tool(builtinTool(objectMapper, "research_reflect", ToolGroup.WORKSPACE))
                .arguments(arguments)
                .build());

        assertThat(result.output().path("status").asText()).isEqualTo("reflected");
        assertThat(result.output().path("needsMoreEvidence").asBoolean()).isTrue();
        assertThat(result.output().path("coverage").asText()).isEqualTo("thin");
        assertThat(result.output().path("nextActions")).isNotEmpty();
        assertThat(result.output().path("missingEvidence")).isNotEmpty();
    }

    @Test
    void weatherToolReturnsStructuredForecast() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        BuiltinToolExecutor executor = new BuiltinToolExecutor(
                new WorkspaceManager(tempDir),
                new SkillRegistry(tempDir.resolve("skills"), new McpServerRegistry(writeExtensionsConfig(), objectMapper)),
                null,
                new BuiltinWeatherClient(objectMapper, Duration.ofSeconds(5)) {
                    @Override
                    public JsonNode forecast(String location, Integer dayOffset, Integer days) {
                        var result = objectMapper.createObjectNode();
                        result.put("location", location);
                        result.put("dayOffset", dayOffset == null ? 0 : dayOffset);
                        result.put("days", days == null ? 1 : days);
                        result.put("condition", "Cloudy");
                        result.put("temperatureMin", "2");
                        result.put("temperatureMax", "6");
                        result.put("wind", "NW 10 km/h");
                        result.put("precipitation", "10% chance");
                        result.set("forecastDays", objectMapper.createArrayNode().add(
                                objectMapper.createObjectNode().put("date", "2026-03-15").put("condition", "Cloudy")
                        ));
                        result.set("source", objectMapper.createObjectNode()
                                .put("provider", "wttr.in")
                                .put("title", "Weather forecast for Tianjin")
                                .put("domain", "wttr.in")
                                .put("url", "https://wttr.in/Tianjin?format=j1"));
                        return result;
                    }
                },
                objectMapper
        );

        ToolExecutionResult result = executor.execute(ToolExecutionRequest.builder()
                .userId(USER_ID)
                .threadId(THREAD_ID)
                .runId(RUN_ID)
                .tool(builtinTool(objectMapper, "weather", ToolGroup.SEARCH))
                .arguments(objectMapper.createObjectNode()
                        .put("location", "Tianjin")
                        .put("dayOffset", 1)
                        .put("days", 1))
                .build());

        assertThat(result.output().path("location").asText()).isEqualTo("Tianjin");
        assertThat(result.output().path("condition").asText()).isEqualTo("Cloudy");
        assertThat(result.output().path("source").path("domain").asText()).isEqualTo("wttr.in");
    }

    @Test
    void loadSkillReturnsManifestFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Path skillsRoot = tempDir.resolve("skills");
        Path skillDir = skillsRoot.resolve("weather");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: weather
                description: Forecast skill.
                summary: Use this for forecasts.
                preferredTools:
                  - weather
                allowedTools:
                  - weather
                  - web_search
                resources:
                  - references/forecast.md
                mcpServers:
                  - search
                invocation: auto
                execution: inline
                requiresDocuments: false
                requiresWeb: true
                agent: general-agent
                ---
                # Weather
                """);
        SkillRegistry registry = new SkillRegistry(skillsRoot, new McpServerRegistry(writeExtensionsConfig(), objectMapper));
        BuiltinToolExecutor executor = new BuiltinToolExecutor(
                new WorkspaceManager(tempDir),
                registry,
                null,
                new BuiltinWeatherClient(objectMapper, Duration.ofSeconds(5)),
                objectMapper
        );

        ToolExecutionResult result = executor.execute(ToolExecutionRequest.builder()
                .userId(USER_ID)
                .threadId(THREAD_ID)
                .runId(RUN_ID)
                .tool(builtinTool(objectMapper, "load_skill", ToolGroup.WORKSPACE))
                .arguments(objectMapper.createObjectNode().put("skillId", "weather"))
                .skillRuntimeSnapshot(registry.snapshotForUser(USER_ID))
                .build());

        assertThat(result.output().path("skillId").asText()).isEqualTo("weather");
        assertThat(result.output().path("sourceKey").asText()).isEqualTo("public:weather");
        assertThat(result.output().path("allowedTools")).hasSize(2);
        assertThat(result.output().path("resources").get(0).asText()).isEqualTo("references/forecast.md");
        assertThat(result.output().path("mcpServers").get(0).asText()).isEqualTo("search");
        assertThat(result.output().path("invocation").asText()).isEqualTo("auto");
        assertThat(result.output().path("execution").asText()).isEqualTo("inline");
        assertThat(result.output().path("status").asText()).isEqualTo("ready");
        assertThat(result.output().path("statusReason").asText()).isEmpty();
        assertThat(result.output().path("alreadyLoaded").asBoolean()).isFalse();
        assertThat(result.output().path("loadStatus").asText()).isEqualTo("loaded");
        assertThat(result.output().path("body").asText()).contains("# Weather");
    }

    @Test
    void loadSkillMarksAlreadyLoadedWhenSkillIsActive() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Path skillsRoot = tempDir.resolve("skills");
        Path skillDir = skillsRoot.resolve("weather");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: weather
                description: Forecast skill.
                agent: general-agent
                ---
                # Weather
                """);
        SkillRegistry registry = new SkillRegistry(skillsRoot, new McpServerRegistry(writeExtensionsConfig(), objectMapper));
        BuiltinToolExecutor executor = new BuiltinToolExecutor(
                new WorkspaceManager(tempDir),
                registry,
                null,
                new BuiltinWeatherClient(objectMapper, Duration.ofSeconds(5)),
                objectMapper
        );

        ToolExecutionResult result = executor.execute(ToolExecutionRequest.builder()
                .userId(USER_ID)
                .threadId(THREAD_ID)
                .runId(RUN_ID)
                .tool(builtinTool(objectMapper, "load_skill", ToolGroup.WORKSPACE))
                .arguments(objectMapper.createObjectNode().put("skillId", "weather"))
                .skillRuntimeSnapshot(registry.snapshotForUser(USER_ID))
                .activeSkillIds(java.util.List.of("weather"))
                .build());

        assertThat(result.output().path("skillId").asText()).isEqualTo("weather");
        assertThat(result.output().path("alreadyLoaded").asBoolean()).isTrue();
        assertThat(result.output().path("loadStatus").asText()).isEqualTo("already_loaded");
    }

    @Test
    void loadSkillResourceReturnsBoundedDeclaredContent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Path skillsRoot = tempDir.resolve("skills");
        Path skillDir = skillsRoot.resolve("weather");
        Path referencesDir = skillDir.resolve("references");
        Files.createDirectories(referencesDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: weather
                description: Forecast skill.
                resources:
                  - references/forecast.md
                agent: general-agent
                ---
                # Weather
                """);
        Files.writeString(referencesDir.resolve("forecast.md"), "Line one.\nLine two.\nLine three.");
        SkillRegistry registry = new SkillRegistry(skillsRoot, new McpServerRegistry(writeExtensionsConfig(), objectMapper));
        BuiltinToolExecutor executor = new BuiltinToolExecutor(
                new WorkspaceManager(tempDir),
                registry,
                null,
                new BuiltinWeatherClient(objectMapper, Duration.ofSeconds(5)),
                objectMapper
        );

        ToolExecutionResult result = executor.execute(ToolExecutionRequest.builder()
                .userId(USER_ID)
                .threadId(THREAD_ID)
                .runId(RUN_ID)
                .tool(builtinTool(objectMapper, "load_skill_resource", ToolGroup.WORKSPACE))
                .arguments(objectMapper.createObjectNode()
                        .put("skillId", "weather")
                        .put("resourcePath", "references/forecast.md")
                        .put("maxChars", 12))
                .skillRuntimeSnapshot(registry.snapshotForUser(USER_ID))
                .build());

        assertThat(result.output().path("skillId").asText()).isEqualTo("weather");
        assertThat(result.output().path("resourcePath").asText()).isEqualTo("references/forecast.md");
        assertThat(result.output().path("resolvedPath").asText()).endsWith("references\\forecast.md");
        assertThat(result.output().path("content").asText()).isEqualTo("Line one.\nLi");
        assertThat(result.output().path("truncated").asBoolean()).isTrue();
    }

    private ToolDescriptor builtinTool(ObjectMapper objectMapper, String name, ToolGroup group) {
        return new ToolDescriptor(
                name,
                "test tool",
                objectMapper.createObjectNode(),
                group,
                "builtin"
        );
    }

    private Path writeExtensionsConfig() throws Exception {
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
        return configPath;
    }
}
