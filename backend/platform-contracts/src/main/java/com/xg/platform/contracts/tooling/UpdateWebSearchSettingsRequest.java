package com.xg.platform.contracts.tooling;

public record UpdateWebSearchSettingsRequest(
        String provider,
        String tavilyApiKey,
        String searchApiBaseUrl
) {
}
