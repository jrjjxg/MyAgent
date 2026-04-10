package com.xg.platform.skill.domain;

import java.nio.file.Path;

public record SkillResourceContent(
        String skillId,
        String resourcePath,
        Path resolvedPath,
        String text,
        boolean truncated
) {
}
