package com.xg.platform.contracts.shared.model;

public record ModelProviderStatusRecord(
        String providerId,
        String displayName,
        boolean enabled,
        boolean ready,
        boolean apiKeyConfigured,
        boolean customApiKeyConfigured,
        boolean systemApiKeyConfigured,
        String effectiveModel,
        String customModel,
        String effectiveBaseUrl,
        String customBaseUrl,
        boolean supportsCustomBaseUrl
) {
}
