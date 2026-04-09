package com.xg.platform.contracts.search;

public record UpdateWebSearchSettingsRequest(
        String provider,
        String tavilyApiKey,
        String searchApiBaseUrl
) {
}
