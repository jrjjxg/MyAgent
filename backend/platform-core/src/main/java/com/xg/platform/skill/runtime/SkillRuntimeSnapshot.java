package com.xg.platform.skill.runtime;

import com.xg.platform.contracts.skill.SkillDescriptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import com.xg.platform.skill.domain.SkillDefinition;
import com.xg.platform.skill.domain.SkillPackageCommand;

public final class SkillRuntimeSnapshot {

    private final String userId;
    private final List<SkillDefinition> skills;
    private final List<SkillDescriptor> descriptors;
    private final Map<String, SkillDefinition> bySourceKey;
    private final Map<String, SkillDefinition> byAlias;

    public SkillRuntimeSnapshot(String userId, List<SkillDefinition> skills) {
        this.userId = userId;
        this.skills = List.copyOf(skills == null ? List.of() : skills);
        Map<String, SkillDefinition> sourceKeyMap = new LinkedHashMap<>();
        Map<String, SkillDefinition> aliasMap = new LinkedHashMap<>();
        List<SkillDescriptor> descriptorList = new ArrayList<>();
        for (SkillDefinition skill : this.skills) {
            sourceKeyMap.put(normalize(skill.sourceKey()), skill);
            aliasMap.putIfAbsent(normalize(skill.skillId()), skill);
        }
        this.skills.stream()
                .sorted(Comparator.comparing(SkillDefinition::skillId)
                        .thenComparing(SkillDefinition::sourceKey))
                .map(this::toDescriptor)
                .forEach(descriptorList::add);
        this.bySourceKey = Map.copyOf(sourceKeyMap);
        this.byAlias = Map.copyOf(aliasMap);
        this.descriptors = List.copyOf(descriptorList);
    }

    public String userId() {
        return userId;
    }

    public List<SkillDefinition> skills() {
        return skills;
    }

    public List<SkillDescriptor> descriptors() {
        return descriptors;
    }

    public SkillDefinition requireEnabledSkill(String skillIdOrSourceKey) {
        SkillDefinition skill = resolve(skillIdOrSourceKey);
        if (skill == null || !skill.enabled()) {
            throw new NoSuchElementException("Unknown or disabled skill: " + skillIdOrSourceKey);
        }
        return skill;
    }

    public SkillDefinition resolve(String skillIdOrSourceKey) {
        String key = normalize(skillIdOrSourceKey);
        if (key.isBlank()) {
            return null;
        }
        SkillDefinition byQualified = bySourceKey.get(key);
        if (byQualified != null) {
            return byQualified;
        }
        return byAlias.get(key);
    }

    private SkillDescriptor toDescriptor(SkillDefinition skill) {
        return new SkillDescriptor(
                skill.skillId(),
                skill.sourceKey(),
                skill.description(),
                skill.summary(),
                skill.homepage(),
                skill.primaryEnv(),
                skill.requiredEnvs(),
                skill.triggers(),
                skill.preferredTools(),
                skill.allowedTools(),
                skill.resources(),
                skill.mcpServers(),
                skill.packageCommands().stream().map(SkillPackageCommand::commandId).toList(),
                skill.requiresDocuments(),
                skill.requiresWeb(),
                skill.agent(),
                skill.invocation().configValue(),
                skill.execution().configValue(),
                skill.enabled(),
                skill.source(),
                skill.sourcePath().toString(),
                skill.availabilityStatus().configValue(),
                skill.availabilityReason()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
