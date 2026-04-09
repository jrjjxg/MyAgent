package com.xg.platform.tools;

public interface WebSearchSettingsResolver {

    ResolvedWebSearchSettings resolve(String userId);

    record ResolvedWebSearchSettings(
            String provider,
            String searchApiBaseUrl,
            String tavilyApiKey
    ) {
    }
}
