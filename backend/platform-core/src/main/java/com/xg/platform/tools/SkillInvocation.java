package com.xg.platform.tools;

public enum SkillInvocation {
    AUTO,
    MANUAL,
    WORKFLOW;

    public static SkillInvocation fromFrontMatter(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return switch (value.trim().toLowerCase()) {
            case "manual" -> MANUAL;
            case "workflow" -> WORKFLOW;
            default -> AUTO;
        };
    }

    public String configValue() {
        return name().toLowerCase();
    }
}
