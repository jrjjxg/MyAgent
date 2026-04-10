package com.xg.platform.tooling.port;

public interface WebSearchSettingsResolver {

    ResolvedWebSearchSettings resolve(String userId);

    record ResolvedWebSearchSettings(
            String provider,
            String searchApiBaseUrl,
            String tavilyApiKey
    ) {
    }
}
