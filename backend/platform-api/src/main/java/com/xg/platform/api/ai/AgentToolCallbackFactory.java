package com.xg.platform.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.ToolExecutionGuard;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.contracts.research.ResearchSourceKind;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class AgentToolCallbackFactory {

    private static final Logger logger = Logger.getLogger(AgentToolCallbackFactory.class.getName());
    private static final long DEFAULT_TOOL_TIMEOUT_MS = 30_000L;

    private final AgentToolService agentToolService;
    private final boolean logAgentFlow;

    AgentToolCallbackFactory(AgentToolService agentToolService, boolean logAgentFlow) {
        this.agentToolService = agentToolService;
        this.logAgentFlow = logAgentFlow;
    }

    List<ToolCallback> create(String providerId,
                              AgentExecutionRequest request,
                              List<ToolDescriptor> availableTools,
                              AgentOutputEmitter outputEmitter,
                              AgentSourceCollector sourceCollector) {
        if (availableTools == null || availableTools.isEmpty()) {
            return List.of();
        }
        return availableTools.stream()
                .map(tool -> FunctionToolCallback.<JsonNode, JsonNode>builder(tool.name(), (arguments, toolContext) -> executeTool(
                                providerId,
                                request,
                                tool,
                                arguments,
                                outputEmitter,
                                sourceCollector
                        ))
                        .description(tool.description())
                        .inputType(JsonNode.class)
                        .inputSchema(tool.inputSchema() == null ? "{}" : tool.inputSchema().toString())
                        .build())
                .map(ToolCallback.class::cast)
                .toList();
    }

    private JsonNode executeTool(String providerId,
                                 AgentExecutionRequest request,
                                 ToolDescriptor tool,
                                 JsonNode arguments,
                                 AgentOutputEmitter outputEmitter,
                                 AgentSourceCollector sourceCollector) {
        if (request.toolUseLimits() != null && !request.toolUseLimits().tryAcquire(tool.name())) {
            outputEmitter.emitEvent(RunEventType.RESEARCH_AGENT_BUDGET_EXHAUSTED, Map.of(
                    "providerId", providerId,
                    "toolName", tool.name(),
                    "maxToolCalls", request.toolUseLimits().maxToolCalls(),
                    "maxSearchCalls", request.toolUseLimits().maxSearchCalls(),
                    "maxFetchCalls", request.toolUseLimits().maxFetchCalls(),
                    "totalCalls", request.toolUseLimits().totalCalls(),
                    "searchCalls", request.toolUseLimits().searchCalls(),
                    "fetchCalls", request.toolUseLimits().fetchCalls(),
                    "reflectCalls", request.toolUseLimits().reflectCalls()
            ));
            return JsonNodeFactory.instance.objectNode()
                    .put("status", "error")
                    .put("toolName", tool.name())
                    .put("error", "Tool budget exhausted");
        }
        outputEmitter.emitEvent(RunEventType.TOOL_STARTED, Map.of(
                "providerId", providerId,
                "toolName", tool.name()
        ));
        logFlow(() -> "tool execution started"
                + " provider=" + providerId
                + " tool=" + tool.name()
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " user=" + request.userId()
                + System.lineSeparator()
                + (arguments == null ? "{}" : arguments.toString()));
        try {
            ToolDescriptor resolvedTool = agentToolService.requireTool(request.userId(), tool.name());
            ToolExecutionResult executionResult = ToolExecutionGuard.execute(
                    tool.name(),
                    request.toolUseLimits() == null ? DEFAULT_TOOL_TIMEOUT_MS : request.toolUseLimits().timeoutMs(),
                    () -> agentToolService.execute(ToolExecutionRequest.builder()
                            .userId(request.userId())
                            .threadId(request.threadId())
                            .runId(request.runId())
                            .tool(resolvedTool)
                            .arguments(arguments)
                            .skillRuntimeSnapshot(request.skillRuntimeSnapshot())
                            .activeSkillIds(request.activeSkillIds())
                            .allowedDocumentIds(request.selectedDocumentIds())
                            .build())
            );
            sourceCollector.capture(tool.name(), executionResult.output());
            emitEvidenceEvents(tool.name(), executionResult.output(), outputEmitter);
            emitSkillLoadEvents(tool.name(), executionResult.output(), outputEmitter);
            emitResearchUpgradeEvents(tool.name(), executionResult.output(), outputEmitter);
            outputEmitter.emitEvent(RunEventType.TOOL_COMPLETED, Map.of(
                    "providerId", providerId,
                    "toolName", tool.name()
            ));
            logFlow(() -> "tool execution completed"
                    + " provider=" + providerId
                    + " tool=" + tool.name()
                    + " thread=" + request.threadId()
                    + " run=" + request.runId()
                    + " user=" + request.userId()
                    + System.lineSeparator()
                    + (executionResult.output() == null ? "{}" : executionResult.output().toString()));
            return executionResult.output();
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "tool execution failed"
                    + " provider=" + providerId
                    + " tool=" + tool.name()
                    + " thread=" + request.threadId()
                    + " run=" + request.runId()
                    + " user=" + request.userId()
                    + " arguments=" + summarize(arguments == null ? "" : arguments.toString()), exception);
            String errorMessage = exception.getMessage() == null ? "Tool execution failed" : exception.getMessage();
            outputEmitter.emitEvent(RunEventType.TOOL_FAILED, Map.of(
                    "providerId", providerId,
                    "toolName", tool.name(),
                    "error", errorMessage
            ));
            return JsonNodeFactory.instance.objectNode()
                    .put("status", "error")
                    .put("toolName", tool.name())
                    .put("error", errorMessage);
        }
    }

    private void emitEvidenceEvents(String toolName,
                                    JsonNode output,
                                    AgentOutputEmitter outputEmitter) {
        if ("web_search".equals(toolName)) {
            for (JsonNode resultNode : output.path("results")) {
                String url = resultNode.path("url").asText("").trim();
                String title = resultNode.path("title").asText(url).trim();
                if (url.isBlank() || title.isBlank()) {
                    continue;
                }
                outputEmitter.emitEvent(RunEventType.EVIDENCE_CANDIDATE_ADDED, Map.of(
                        "kind", ResearchSourceKind.WEB_RESULT.name(),
                        "title", title,
                        "domain", AgentSourceCollector.domainOf(url),
                        "url", url
                ));
            }
            return;
        }
        if ("web_fetch".equals(toolName)) {
            String url = output.path("url").asText("").trim();
            String title = output.path("title").asText(url).trim();
            if (url.isBlank() || title.isBlank()) {
                return;
            }
            outputEmitter.emitEvent(RunEventType.EVIDENCE_VERIFIED_ADDED, Map.of(
                    "kind", ResearchSourceKind.WEB_PAGE.name(),
                    "title", title,
                    "domain", AgentSourceCollector.domainOf(url),
                    "url", url
            ));
            return;
        }
        if ("weather".equals(toolName)) {
            JsonNode source = output.path("source");
            String url = source.path("url").asText("").trim();
            String title = source.path("title").asText("").trim();
            if (url.isBlank() || title.isBlank()) {
                return;
            }
            outputEmitter.emitEvent(RunEventType.EVIDENCE_VERIFIED_ADDED, Map.of(
                    "kind", ResearchSourceKind.WEATHER_REPORT.name(),
                    "title", title,
                    "domain", source.path("domain").asText("wttr.in").trim(),
                    "url", url
            ));
        }
    }

    private void emitSkillLoadEvents(String toolName,
                                     JsonNode output,
                                     AgentOutputEmitter outputEmitter) {
        if (!"load_skill".equals(toolName)) {
            return;
        }
        String skillId = output.path("skillId").asText("").trim();
        if (skillId.isBlank()) {
            return;
        }
        outputEmitter.emitEvent(RunEventType.SKILL_LOADED, Map.of(
                "skillId", skillId,
                "source", output.path("source").asText("").trim(),
                "path", output.path("path").asText("").trim(),
                "loadReason", "loaded-on-demand"
        ));
    }

    private void emitResearchUpgradeEvents(String toolName,
                                           JsonNode output,
                                           AgentOutputEmitter outputEmitter) {
        if (!"suggest_deep_research".equals(toolName)) {
            return;
        }
        String reason = output.path("reason").asText("").trim();
        String suggestedBrief = output.path("suggestedBrief").asText("").trim();
        if (reason.isBlank() || suggestedBrief.isBlank()) {
            return;
        }
        outputEmitter.emitEvent(RunEventType.RESEARCH_UPGRADE_SUGGESTED, Map.of(
                "reason", reason,
                "suggestedTitle", output.path("suggestedTitle").asText("").trim(),
                "suggestedBrief", suggestedBrief
        ));
    }

    private String summarize(String response) {
        String normalized = response == null ? "" : response.trim().replaceAll("\\s+", " ");
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    private void logFlow(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }
}
