package com.xg.platform.skill.domain;

public enum SkillAvailabilityStatus {
    READY,
    MISSING_ENV,
    DISABLED,
    UNAVAILABLE;

    public String configValue() {
        return name().toLowerCase();
    }
}
