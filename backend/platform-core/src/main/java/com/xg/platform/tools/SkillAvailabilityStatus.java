package com.xg.platform.tools;

public enum SkillAvailabilityStatus {
    READY,
    MISSING_ENV,
    DISABLED,
    UNAVAILABLE;

    public String configValue() {
        return name().toLowerCase();
    }
}
