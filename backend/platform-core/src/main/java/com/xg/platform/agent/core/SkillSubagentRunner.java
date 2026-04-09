package com.xg.platform.agent.core;

import com.xg.platform.tools.SkillDefinition;
import com.xg.platform.tools.ToolDescriptor;

import java.util.List;
import java.util.Locale;

public class SkillSubagentRunner {

    private final AgentTurnExecutionSupport agentTurnExecutionSupport;

    public SkillSubagentRunner(AgentTurnExecutionSupport agentTurnExecutionSupport) {
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
    }

    public String run(String providerId,
                      AgentExecutionRequest request,
                      SkillDefinition skill,
                      String boundedTask,
                      List<ToolDescriptor> availableTools,
                      AgentOutputEmitter outputEmitter) {
        List<ToolDescriptor> skillTools = SkillRuntimeSupport.filterToolsForSkills(availableTools, List.of(skill));
        AgentExecutionRequest isolatedRequest = new AgentExecutionRequest(
                request.userId(),
                request.threadId(),
                request.runId(),
                boundedTask,
                request.agentId(),
                request.providerId(),
                request.requestedCapabilities(),
                List.of(skill.skillId()),
                "manual",
                request.artifacts(),
                request.uploadedFiles(),
                request.inputImages(),
                request.recentMessages(),
                request.sessionSummary(),
                request.longTermMemory(),
                request.chatRouteKind(),
                request.skillRuntimeSnapshot(),
                request.toolUseLimits(),
                List.of(skill.skillId()),
                request.selectedDocumentIds()
        );
        String prompt = """
                You are an isolated skill subagent.
                Work only on the bounded task below and return a concise markdown note.
                Do not assume access to prior conversation unless it is restated here.

                Active skill:
                - skillId: %s
                - sourceKey: %s
                - invocation: %s
                - execution: %s

                Skill instructions:
                %s
                """.formatted(
                skill.skillId(),
                skill.sourceKey(),
                skill.invocation().configValue(),
                skill.execution().configValue(),
                skill.body().trim()
        );
        return agentTurnExecutionSupport.runModelLoop(
                normalizeProvider(providerId, request.providerId()),
                isolatedRequest,
                prompt,
                skillTools,
                outputEmitter
        );
    }

    private String normalizeProvider(String providerId, String fallbackProviderId) {
        String candidate = providerId == null || providerId.isBlank() ? fallbackProviderId : providerId;
        return candidate == null ? null : candidate.trim().toLowerCase(Locale.ROOT);
    }
}
