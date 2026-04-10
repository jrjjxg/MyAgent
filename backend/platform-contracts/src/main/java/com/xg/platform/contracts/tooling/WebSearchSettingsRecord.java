package com.xg.platform.contracts.tooling;

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
