package com.xg.platform.agent.core;

import com.xg.platform.tools.SkillDefinition;
import com.xg.platform.tools.SkillExecutionMode;
import com.xg.platform.tools.ToolDescriptor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SkillRuntimeSupport {

    private SkillRuntimeSupport() {
    }

    public static ActivatedSkillContext activate(List<SkillDefinition> skills, List<ToolDescriptor> availableTools) {
        List<SkillDefinition> safeSkills = skills == null ? List.of() : List.copyOf(skills);
        List<SkillDefinition> inlineSkills = safeSkills.stream()
                .filter(skill -> skill.execution() != SkillExecutionMode.SUBAGENT)
                .toList();
        List<SkillDefinition> subagentSkills = safeSkills.stream()
                .filter(skill -> skill.execution() == SkillExecutionMode.SUBAGENT)
                .toList();
        return new ActivatedSkillContext(
                safeSkills,
                inlineSkills,
                subagentSkills,
                filterToolsForSkills(availableTools, safeSkills)
        );
    }

    public static List<ToolDescriptor> filterToolsForSkills(List<ToolDescriptor> availableTools,
                                                            List<SkillDefinition> activeSkills) {
        if (availableTools == null || availableTools.isEmpty() || activeSkills == null || activeSkills.isEmpty()) {
            return availableTools == null ? List.of() : List.copyOf(availableTools);
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (SkillDefinition skill : activeSkills) {
            if (skill.allowedTools() == null || skill.allowedTools().isEmpty()) {
                continue;
            }
            allowed.addAll(skill.allowedTools());
        }
        if (allowed.isEmpty()) {
            return List.copyOf(availableTools);
        }
        return availableTools.stream()
                .filter(tool -> allowed.contains(tool.name()))
                .toList();
    }

    public static List<ToolDescriptor> prioritizeToolsForSkills(List<ToolDescriptor> availableTools,
                                                                List<SkillDefinition> activeSkills) {
        if (availableTools == null || availableTools.isEmpty() || activeSkills == null || activeSkills.isEmpty()) {
            return availableTools == null ? List.of() : List.copyOf(availableTools);
        }
        Set<String> preferred = new LinkedHashSet<>();
        for (SkillDefinition skill : activeSkills) {
            if (skill.preferredTools() == null || skill.preferredTools().isEmpty()) {
                continue;
            }
            preferred.addAll(skill.preferredTools());
        }
        if (preferred.isEmpty()) {
            return List.copyOf(availableTools);
        }
        java.util.Map<String, ToolDescriptor> toolsByName = new java.util.LinkedHashMap<>();
        for (ToolDescriptor tool : availableTools) {
            toolsByName.put(tool.name(), tool);
        }
        List<ToolDescriptor> prioritized = new java.util.ArrayList<>(availableTools.size());
        for (String preferredToolName : preferred) {
            ToolDescriptor tool = toolsByName.get(preferredToolName);
            if (tool != null) {
                prioritized.add(tool);
            }
        }
        for (ToolDescriptor tool : availableTools) {
            if (!preferred.contains(tool.name())) {
                prioritized.add(tool);
            }
        }
        return List.copyOf(prioritized);
    }

    public record ActivatedSkillContext(
            List<SkillDefinition> activeSkills,
            List<SkillDefinition> inlineSkills,
            List<SkillDefinition> subagentSkills,
            List<ToolDescriptor> availableTools
    ) {
        public List<String> skillIds() {
            return activeSkills.stream().map(SkillDefinition::skillId).distinct().toList();
        }
    }
}
