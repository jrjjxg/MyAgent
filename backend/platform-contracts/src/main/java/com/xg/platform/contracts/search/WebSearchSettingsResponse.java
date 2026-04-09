package com.xg.platform.contracts.search;

public record WebSearchSettingsResponse(
        boolean secretStorageAvailable,
        WebSearchSettingsRecord settings
) {
}
