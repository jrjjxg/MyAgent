package com.xg.platform.contracts.skill;

public record UpsertSkillRequest(
        String skillId,
        String description,
        String agent,
        String body,
        Boolean enabled
) {
}
