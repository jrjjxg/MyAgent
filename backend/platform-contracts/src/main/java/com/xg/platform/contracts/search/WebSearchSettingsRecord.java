package com.xg.platform.contracts.search;

public record WebSearchSettingsRecord(
        String effectiveProvider,
        String customProvider,
        boolean tavilyApiKeyConfigured,
        boolean customTavilyApiKeyConfigured,
        boolean systemTavilyApiKeyConfigured,
        String effectiveSearchApiBaseUrl,
        String customSearchApiBaseUrl
) {
}
