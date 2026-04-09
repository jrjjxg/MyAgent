package com.xg.platform.tools;

public enum SkillExecutionMode {
    INLINE,
    SUBAGENT;

    public static SkillExecutionMode fromFrontMatter(String value) {
        if (value == null || value.isBlank()) {
            return INLINE;
        }
        return switch (value.trim().toLowerCase()) {
            case "subagent" -> SUBAGENT;
            default -> INLINE;
        };
    }

    public String configValue() {
        return name().toLowerCase();
    }
}
