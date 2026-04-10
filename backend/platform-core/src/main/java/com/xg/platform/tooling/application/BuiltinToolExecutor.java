package com.xg.platform.tooling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.workspace.application.WorkspaceManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.skill.domain.SkillDefinition;
import com.xg.platform.skill.domain.SkillPackageCommand;
import com.xg.platform.skill.domain.SkillResourceContent;
import com.xg.platform.skill.runtime.SkillPackageExecutor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;

public class BuiltinToolExecutor {

    private final WorkspaceManager workspaceManager;
    private final SkillRegistry skillRegistry;
    private final BuiltinWebResearchClient webResearchClient;
    private final BuiltinWeatherClient weatherClient;
    private final SkillPackageExecutor skillPackageExecutor;
    private final WorkspaceDocumentToolSupport workspaceDocumentToolSupport;
    private final ObjectMapper objectMapper;

    public BuiltinToolExecutor(WorkspaceManager workspaceManager,
                               SkillRegistry skillRegistry,
                               BuiltinWebResearchClient webResearchClient,
                               BuiltinWeatherClient weatherClient,
                               SkillPackageExecutor skillPackageExecutor,
                               WorkspaceDocumentToolSupport workspaceDocumentToolSupport,
                               ObjectMapper objectMapper) {
        this.workspaceManager = workspaceManager;
        this.skillRegistry = skillRegistry;
        this.webResearchClient = webResearchClient;
        this.weatherClient = weatherClient;
        this.skillPackageExecutor = skillPackageExecutor;
        this.workspaceDocumentToolSupport = workspaceDocumentToolSupport;
        this.objectMapper = objectMapper;
    }

    public BuiltinToolExecutor(WorkspaceManager workspaceManager,
                               SkillRegistry skillRegistry,
                               BuiltinWebResearchClient webResearchClient,
                               BuiltinWeatherClient weatherClient,
                               SkillPackageExecutor skillPackageExecutor,
                               ObjectMapper objectMapper) {
        this(
                workspaceManager,
                skillRegistry,
                webResearchClient,
                weatherClient,
                skillPackageExecutor,
                null,
                objectMapper
        );
    }

    public BuiltinToolExecutor(WorkspaceManager workspaceManager,
                               SkillRegistry skillRegistry,
                               BuiltinWebResearchClient webResearchClient,
                               BuiltinWeatherClient weatherClient,
                               ObjectMapper objectMapper) {
        this(
                workspaceManager,
                skillRegistry,
                webResearchClient,
                weatherClient,
                new SkillPackageExecutor(
                        skillRegistry,
                        workspaceManager,
                        objectMapper,
                        List.of("python"),
                        java.time.Duration.ofSeconds(30)
                ),
                null,
                objectMapper
        );
    }

    public ToolExecutionResult execute(ToolExecutionRequest request) {
        return execute(request, Map.of());
    }

    public ToolExecutionResult execute(ToolExecutionRequest request, Map<String, String> envOverrides) {
        return switch (request.tool().name()) {
            case "list_workspace_documents", "inspect_document", "list_document_sections", "search_document", "read_document" ->
                    requireWorkspaceDocumentToolSupport().execute(request);
            case "write_workspace_note" -> writeWorkspaceNote(request);
            case "load_skill" -> loadSkill(request);
            case "load_skill_resource" -> loadSkillResource(request);
            case "run_skill_command" -> skillPackageExecutor.runCommand(request, envOverrides);
            case "skill_process_status" -> skillPackageExecutor.processStatus(request);
            case "stop_skill_process" -> skillPackageExecutor.stopProcess(request);
            case "weather" -> weather(request);
            case "web_search" -> webSearch(request);
            case "web_fetch" -> webFetch(request);
            case "suggest_deep_research" -> suggestDeepResearch(request);
            case "ask_clarification" -> askClarification(request);
            case "research_reflect" -> researchReflect(request);
            default -> throw new IllegalArgumentException("Unsupported builtin tool: " + request.tool().name());
        };
    }

    private WorkspaceDocumentToolSupport requireWorkspaceDocumentToolSupport() {
        if (workspaceDocumentToolSupport == null) {
            throw new IllegalStateException("Workspace document tools are not configured");
        }
        return workspaceDocumentToolSupport;
    }

    private ToolExecutionResult writeWorkspaceNote(ToolExecutionRequest request) {
        JsonNode arguments = request.arguments();
        String content = arguments.path("content").asText("").trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("write_workspace_note requires non-blank content");
        }
        String relativePath = arguments.path("relativePath").asText("").trim();
        if (relativePath.isBlank()) {
            relativePath = request.runId() + "/notes/tool-note.md";
        }
        Path path = workspaceManager.resolvePath(request.userId(), request.threadId(), WorkspaceArea.WORKSPACE, relativePath);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            JsonNode result = objectMapper.createObjectNode()
                    .put("relativePath", relativePath.replace('\\', '/'))
                    .put("bytesWritten", Files.size(path));
            return new ToolExecutionResult(request.tool().name(), result, false, "ok");
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write workspace note", exception);
        }
    }

    private ToolExecutionResult loadSkill(ToolExecutionRequest request) {
        String skillId = request.arguments().path("skillId").asText("").trim();
        if (skillId.isBlank()) {
            throw new IllegalArgumentException("load_skill requires skillId");
        }
        boolean alreadyLoaded = request.activeSkillIds().contains(skillId);
        SkillDefinition skill = skillRegistry.loadSkillContent(request.userId(), skillId);
        var result = objectMapper.createObjectNode();
        result.put("skillId", skill.skillId());
        result.put("alreadyLoaded", alreadyLoaded);
        result.put("loadStatus", alreadyLoaded ? "already_loaded" : "loaded");
        result.put("sourceKey", skill.sourceKey());
        result.put("description", skill.description());
        result.put("summary", skill.summary() == null ? "" : skill.summary());
        result.put("homepage", skill.homepage());
        result.put("primaryEnv", skill.primaryEnv());
        result.set("requiredEnvs", toArrayNode(skill.requiredEnvs()));
        result.set("triggers", toArrayNode(skill.triggers()));
        result.set("preferredTools", toArrayNode(skill.preferredTools()));
        result.set("allowedTools", toArrayNode(skill.allowedTools()));
        result.set("resources", toArrayNode(skill.resources()));
        result.set("mcpServers", toArrayNode(skill.mcpServers()));
        result.put("requiresDocuments", skill.requiresDocuments());
        result.put("requiresWeb", skill.requiresWeb());
        result.put("agent", skill.agent());
        result.put("invocation", skill.invocation().configValue());
        result.put("execution", skill.execution().configValue());
        result.put("source", skill.source());
        result.put("path", skill.sourcePath().toString());
        result.put("packageRoot", skill.sourcePath().getParent().toString());
        result.put("status", skill.availabilityStatus().configValue());
        result.put("statusReason", skill.availabilityReason());
        var packageCommands = objectMapper.createArrayNode();
        for (SkillPackageCommand command : skill.packageCommands()) {
            packageCommands.add(objectMapper.createObjectNode()
                    .put("commandId", command.commandId())
                    .put("relativePath", command.relativePath())
                    .put("runner", command.runner().configValue())
                    .put("backgroundSuggested", command.backgroundSuggested()));
        }
        result.set("packageCommands", packageCommands);
        result.put("body", skill.body());
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private ToolExecutionResult loadSkillResource(ToolExecutionRequest request) {
        String skillId = request.arguments().path("skillId").asText("").trim();
        String resourcePath = request.arguments().path("resourcePath").asText("").trim();
        Integer maxChars = request.arguments().path("maxChars").canConvertToInt()
                ? request.arguments().path("maxChars").asInt()
                : null;
        if (skillId.isBlank()) {
            throw new IllegalArgumentException("load_skill_resource requires skillId");
        }
        if (resourcePath.isBlank()) {
            throw new IllegalArgumentException("load_skill_resource requires resourcePath");
        }
        SkillResourceContent resource = skillRegistry.loadSkillResource(request.userId(), skillId, resourcePath, maxChars);
        var result = objectMapper.createObjectNode();
        result.put("skillId", resource.skillId());
        result.put("resourcePath", resource.resourcePath());
        result.put("resolvedPath", resource.resolvedPath().toString());
        result.put("content", resource.text());
        result.put("truncated", resource.truncated());
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private ToolExecutionResult weather(ToolExecutionRequest request) {
        String location = request.arguments().path("location").asText("").trim();
        Integer dayOffset = request.arguments().path("dayOffset").canConvertToInt()
                ? request.arguments().path("dayOffset").asInt()
                : null;
        Integer days = request.arguments().path("days").canConvertToInt()
                ? request.arguments().path("days").asInt()
                : null;
        JsonNode result = weatherClient.forecast(location, dayOffset, days);
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private ToolExecutionResult webSearch(ToolExecutionRequest request) {
        String query = request.arguments().path("query").asText("").trim();
        Integer maxResults = request.arguments().path("maxResults").canConvertToInt()
                ? request.arguments().path("maxResults").asInt()
                : null;
        JsonNode result = webResearchClient.search(request.userId(), query, maxResults);
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private ToolExecutionResult webFetch(ToolExecutionRequest request) {
        String url = request.arguments().path("url").asText("").trim();
        JsonNode result = webResearchClient.fetch(url);
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private ToolExecutionResult askClarification(ToolExecutionRequest request) {
        JsonNode arguments = request.arguments();
        var result = objectMapper.createObjectNode();
        result.set("questions", arguments.path("questions").isArray() ? arguments.path("questions") : objectMapper.createArrayNode());
        result.put("reason", arguments.path("reason").asText(""));
        result.put("status", "clarification_requested");
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private ToolExecutionResult suggestDeepResearch(ToolExecutionRequest request) {
        JsonNode arguments = request.arguments();
        String reason = arguments.path("reason").asText("").trim();
        String suggestedBrief = arguments.path("suggestedBrief").asText("").trim();
        String suggestedTitle = arguments.path("suggestedTitle").asText("").trim();
        if (reason.isBlank()) {
            throw new IllegalArgumentException("suggest_deep_research requires reason");
        }
        if (suggestedBrief.isBlank()) {
            throw new IllegalArgumentException("suggest_deep_research requires suggestedBrief");
        }
        var result = objectMapper.createObjectNode();
        result.put("status", "research_upgrade_suggested");
        result.put("reason", reason);
        result.put("suggestedTitle", suggestedTitle);
        result.put("suggestedBrief", suggestedBrief);
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private ToolExecutionResult researchReflect(ToolExecutionRequest request) {
        JsonNode arguments = request.arguments();
        String topic = arguments.path("topic").asText("").trim();
        if (topic.isBlank()) {
            throw new IllegalArgumentException("research_reflect requires topic");
        }
        String query = arguments.path("query").asText("").trim();
        String focus = arguments.path("focus").asText("").trim();
        String evidenceSummary = arguments.path("evidenceSummary").asText("").trim();
        int sourceCount = Math.max(0, arguments.path("sourceCount").asInt(0));
        int stepIndex = Math.max(0, arguments.path("stepIndex").asInt(0));
        int totalSteps = Math.max(0, arguments.path("totalSteps").asInt(0));
        List<String> openQuestions = readStringArray(arguments.path("openQuestions"));
        List<String> completedFindings = readStringArray(arguments.path("completedFindings"));

        String coverage = sourceCount >= 5 ? "strong" : sourceCount >= 3 ? "developing" : "thin";
        String confidence = sourceCount >= 5 && !completedFindings.isEmpty()
                ? "high"
                : sourceCount >= 3
                ? "medium"
                : "low";
        boolean needsMoreEvidence = sourceCount < 3 || !openQuestions.isEmpty() || evidenceSummary.length() < 240;

        List<String> missingEvidence = new ArrayList<>();
        if (sourceCount == 0) {
            missingEvidence.add("No source-backed evidence has been collected yet.");
        } else if (sourceCount < 3) {
            missingEvidence.add("More corroborating sources are needed before final synthesis.");
        }
        if (evidenceSummary.length() < 240) {
            missingEvidence.add("Current evidence summary is still shallow; fetch fuller content from the strongest sources.");
        }
        for (String openQuestion : openQuestions) {
            missingEvidence.add("Unresolved question: " + openQuestion);
        }

        List<String> focusAreas = new ArrayList<>();
        if (!focus.isBlank()) {
            focusAreas.add(focus);
        }
        for (String openQuestion : openQuestions) {
            if (focusAreas.size() >= 3) {
                break;
            }
            focusAreas.add(openQuestion);
        }
        if (focusAreas.isEmpty()) {
            focusAreas.add("authoritative evidence");
            focusAreas.add("counterarguments");
        }

        List<String> nextActions = new ArrayList<>();
        if (sourceCount < 2) {
            nextActions.add("Search for primary or official sources about " + topic + ".");
        }
        if (sourceCount < 4) {
            nextActions.add("Fetch full text from the most relevant sources instead of relying on snippets.");
        }
        if (!focus.isBlank()) {
            nextActions.add("Gather evidence specifically about: " + focus + ".");
        }
        for (String openQuestion : openQuestions) {
            if (nextActions.size() >= 4) {
                break;
            }
            nextActions.add("Resolve this gap: " + openQuestion);
        }
        if (nextActions.isEmpty()) {
            nextActions.add("Proceed to synthesis and keep claims tightly grounded in the collected evidence.");
        }

        String summary = buildReflectionSummary(topic, query, sourceCount, coverage, confidence, stepIndex, totalSteps, focusAreas, missingEvidence);
        var result = objectMapper.createObjectNode();
        result.put("status", "reflected");
        result.put("summary", summary);
        result.put("coverage", coverage);
        result.put("confidence", confidence);
        result.put("needsMoreEvidence", needsMoreEvidence);
        result.set("focusAreas", toArrayNode(focusAreas));
        result.set("nextActions", toArrayNode(nextActions));
        result.set("missingEvidence", toArrayNode(missingEvidence));
        return new ToolExecutionResult(request.tool().name(), result, false, "ok");
    }

    private List<String> readStringArray(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    values.add(item.asText().trim());
                }
            }
        }
        return List.copyOf(values);
    }

    private JsonNode toArrayNode(List<String> values) {
        var array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private String buildReflectionSummary(String topic,
                                          String query,
                                          int sourceCount,
                                          String coverage,
                                          String confidence,
                                          int stepIndex,
                                          int totalSteps,
                                          List<String> focusAreas,
                                          List<String> missingEvidence) {
        StringBuilder builder = new StringBuilder("Research reflection for ");
        builder.append(topic)
                .append(": coverage is ")
                .append(coverage)
                .append(" with ")
                .append(sourceCount)
                .append(" source(s), confidence ")
                .append(confidence)
                .append(".");
        if (!query.isBlank()) {
            builder.append(" Current query: ").append(query).append(".");
        }
        if (stepIndex > 0 && totalSteps > 0) {
            builder.append(" Step ").append(stepIndex).append(" of ").append(totalSteps).append(".");
        }
        if (!focusAreas.isEmpty()) {
            builder.append(" Focus next on ").append(String.join("; ", focusAreas)).append(".");
        }
        if (!missingEvidence.isEmpty()) {
            builder.append(" Gaps: ").append(String.join(" ", missingEvidence));
        }
        return builder.toString().trim();
    }
}
