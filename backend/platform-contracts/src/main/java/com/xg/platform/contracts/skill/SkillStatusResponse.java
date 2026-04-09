package com.xg.platform.contracts.skill;

import java.util.List;

public record SkillStatusResponse(
        boolean secretStorageAvailable,
        List<SkillStatusRecord> skills
) {
}
