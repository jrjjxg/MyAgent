package com.xg.platform.contracts.model;

public record UpdateModelProviderConfigRequest(
        Boolean enabled,
        String apiKey,
        String model,
        String baseUrl
) {
}
