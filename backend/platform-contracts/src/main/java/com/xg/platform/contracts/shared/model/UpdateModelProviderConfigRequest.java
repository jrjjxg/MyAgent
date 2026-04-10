package com.xg.platform.contracts.shared.model;

public record UpdateModelProviderConfigRequest(
        Boolean enabled,
        String apiKey,
        String model,
        String baseUrl
) {
}
