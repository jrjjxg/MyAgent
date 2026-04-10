package com.xg.platform.conversation.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentGraphToolCall;
import com.xg.platform.agent.core.ExecutionSource;
import com.xg.platform.agent.core.ToolExecutionGuard;
import com.xg.platform.agent.core.ToolUseLimits;
import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ConversationToolSupport {

    private static final Logger logger = Logger.getLogger(ConversationToolSupport.class.getName());

    private final AgentToolService agentToolService;
    private final SkillRegistry skillRegistry;
    private final ConversationEventSupport eventSupport;
    private final boolean logAgentFlow;
    private final long timeoutMs;

    ConversationToolSupport(AgentToolService agentToolService,
                            SkillRegistry skillRegistry,
                            ConversationEventSupport eventSupport,
                            boolean logAgentFlow,
                            long timeoutMs) {
        this.agentToolService = agentToolService;
        this.skillRegistry = skillRegistry;
        this.eventSupport = eventSupport;
        this.logAgentFlow = logAgentFlow;
        this.timeoutMs = timeoutMs;
    }

    List<ToolDescriptor> availableTools(String userId) {
        return List.copyOf(agentToolService.listAvailableTools(userId));
    }

    JsonNode executeToolCall(InteractionState state,
                             Consumer<RunEvent> runEventConsumer,
                             ToolUseLimits toolUseLimits,
                             ToolDescriptor tool,
                             AgentGraphToolCall toolCall,
                             List<ExecutionSource> sources,
                             List<String> activeSkillIds,
                             List<String> selectedDocumentIds) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String runId = state.runId().orElseThrow();
        if (toolUseLimits != null && !toolUseLimits.tryAcquire(tool.name())) {
            eventSupport.publishEvent(userId, threadId, runId, RunEventType.RESEARCH_AGENT_BUDGET_EXHAUSTED, Map.of(
                    "providerId", state.providerId().orElse(""),
                    "toolName", tool.name(),
                    "maxToolCalls", toolUseLimits.maxToolCalls(),
                    "maxSearchCalls", toolUseLimits.maxSearchCalls(),
                    "maxFetchCalls", toolUseLimits.maxFetchCalls(),
                    "totalCalls", toolUseLimits.totalCalls(),
                    "searchCalls", toolUseLimits.searchCalls(),
                    "fetchCalls", toolUseLimits.fetchCalls(),
                    "reflectCalls", toolUseLimits.reflectCalls()
            ), runEventConsumer);
            return JsonNodeFactory.instance.objectNode()
                    .put("status", "error")
                    .put("toolName", tool.name())
                    .put("error", "Tool budget exhausted");
        }
        eventSupport.publishEvent(userId, threadId, runId, RunEventType.TOOL_STARTED, Map.of(
                "providerId", state.providerId().orElse(""),
                "toolName", tool.name()
        ), runEventConsumer);
        logFlow(() -> "tool execution started"
                + " provider=" + state.providerId().orElse("")
                + " tool=" + tool.name()
                + " thread=" + threadId
                + " run=" + runId
                + System.lineSeparator()
                + (toolCall.arguments() == null ? "{}" : toolCall.arguments().toString()));
        try {
            ToolExecutionResult result = ToolExecutionGuard.execute(
                    tool.name(),
                    resolveToolTimeoutMs(toolUseLimits),
                    () -> agentToolService.execute(new ToolExecutionRequest(
                            userId,
                            threadId,
                            runId,
                            tool,
                            toolCall.arguments(),
                            skillRegistry.snapshotForUser(userId),
                            activeSkillIds,
                            selectedDocumentIds
                    ))
            );
            JsonNode output = result.output() == null ? JsonNodeFactory.instance.objectNode() : result.output();
            captureSources(tool.name(), output, sources, runEventConsumer, userId, threadId, runId);
            maybeActivateSkill(tool.name(), output, activeSkillIds);
            eventSupport.publishEvent(userId, threadId, runId, RunEventType.TOOL_COMPLETED, Map.of(
                    "providerId", state.providerId().orElse(""),
                    "toolName", tool.name()
            ), runEventConsumer);
            logFlow(() -> "tool execution completed"
                    + " provider=" + state.providerId().orElse("")
                    + " tool=" + tool.name()
                    + " thread=" + threadId
                    + " run=" + runId
                    + System.lineSeparator()
                    + output.toString());
            return output;
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "tool execution failed tool=" + tool.name() + " thread=" + threadId + " run=" + runId, exception);
            eventSupport.publishEvent(userId, threadId, runId, RunEventType.TOOL_FAILED, Map.of(
                    "providerId", state.providerId().orElse(""),
                    "toolName", tool.name(),
                    "error", safeErrorMessage(exception)
            ), runEventConsumer);
            return toolFailurePayload(tool.name(), safeErrorMessage(exception));
        }
    }

    List<com.xg.platform.skill.domain.SkillDefinition> resolveLoadedSkills(String userId,
                                                                           List<String> activeSkillIds) {
        if (activeSkillIds == null || activeSkillIds.isEmpty()) {
            return List.of();
        }
        com.xg.platform.skill.runtime.SkillRuntimeSnapshot snapshot = skillRegistry.snapshotForUser(userId);
        List<com.xg.platform.skill.domain.SkillDefinition> loadedSkills = new ArrayList<>();
        for (String activeSkillId : activeSkillIds) {
            if (activeSkillId == null || activeSkillId.isBlank()) {
                continue;
            }
            try {
                loadedSkills.add(skillRegistry.requireEnabledSkill(userId, activeSkillId, snapshot));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale skill ids so prompt generation remains resilient.
            }
        }
        return List.copyOf(loadedSkills);
    }

    private void maybeActivateSkill(String toolName, JsonNode output, List<String> activeSkillIds) {
        if (!"load_skill".equals(toolName)) {
            return;
        }
        String skillId = output.path("skillId").asText("").trim();
        if (!skillId.isBlank() && !activeSkillIds.contains(skillId)) {
            activeSkillIds.add(skillId);
        }
    }

    private void captureSources(String toolName,
                                JsonNode output,
                                List<ExecutionSource> sources,
                                Consumer<RunEvent> runEventConsumer,
                                String userId,
                                String threadId,
                                String runId) {
        if ("web_search".equals(toolName)) {
            for (JsonNode resultNode : output.path("results")) {
                String url = resultNode.path("url").asText("").trim();
                String title = resultNode.path("title").asText(url).trim();
                if (url.isBlank() || title.isBlank()) {
                    continue;
                }
                ExecutionSource source = new ExecutionSource("WEB_RESULT", title, domainOf(url), url, false, false);
                sources.add(source);
                eventSupport.publishEvent(userId, threadId, runId, RunEventType.EVIDENCE_CANDIDATE_ADDED, Map.of(
                        "kind", source.kind(),
                        "title", source.title(),
                        "domain", source.domain(),
                        "url", source.url()
                ), runEventConsumer);
            }
            return;
        }
        if ("web_fetch".equals(toolName)) {
            String url = output.path("url").asText("").trim();
            String title = output.path("title").asText(url).trim();
            if (url.isBlank() || title.isBlank()) {
                return;
            }
            ExecutionSource source = new ExecutionSource("WEB_PAGE", title, domainOf(url), url, true, true);
            sources.add(source);
            eventSupport.publishEvent(userId, threadId, runId, RunEventType.EVIDENCE_VERIFIED_ADDED, Map.of(
                    "kind", source.kind(),
                    "title", source.title(),
                    "domain", source.domain(),
                    "url", source.url()
            ), runEventConsumer);
            return;
        }
        if ("weather".equals(toolName)) {
            JsonNode sourceNode = output.path("source");
            String url = sourceNode.path("url").asText("").trim();
            String title = sourceNode.path("title").asText("").trim();
            if (url.isBlank() || title.isBlank()) {
                return;
            }
            ExecutionSource source = new ExecutionSource(
                    "WEATHER_REPORT",
                    title,
                    sourceNode.path("domain").asText("wttr.in").trim(),
                    url,
                    true,
                    true
            );
            sources.add(source);
            eventSupport.publishEvent(userId, threadId, runId, RunEventType.EVIDENCE_VERIFIED_ADDED, Map.of(
                    "kind", source.kind(),
                    "title", source.title(),
                    "domain", source.domain(),
                    "url", source.url()
            ), runEventConsumer);
        }
    }

    private String domainOf(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private JsonNode toolFailurePayload(String toolName, String errorMessage) {
        return JsonNodeFactory.instance.objectNode()
                .put("status", "error")
                .put("toolName", toolName == null ? "" : toolName)
                .put("error", errorMessage == null || errorMessage.isBlank() ? "Tool execution failed" : errorMessage);
    }

    private long resolveToolTimeoutMs(ToolUseLimits toolUseLimits) {
        return toolUseLimits == null ? timeoutMs : toolUseLimits.timeoutMs();
    }

    private String safeErrorMessage(RuntimeException exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "Internal failure";
        }
        return exception.getMessage().trim();
    }

    private void logFlow(Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }
}
