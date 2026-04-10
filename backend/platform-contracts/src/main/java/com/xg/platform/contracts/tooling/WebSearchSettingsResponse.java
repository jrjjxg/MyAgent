package com.xg.platform.contracts.tooling;

public record WebSearchSettingsResponse(
        boolean secretStorageAvailable,
        WebSearchSettingsRecord settings
) {
}
