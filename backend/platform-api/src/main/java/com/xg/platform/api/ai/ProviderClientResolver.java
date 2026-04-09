package com.xg.platform.api.ai;

import org.springframework.ai.chat.model.ChatModel;

public interface ProviderClientResolver {

    ResolvedProviderClient resolve(String userId, String requestedProviderId);

    record ResolvedProviderClient(
            String providerId,
            String model,
            ChatModel chatModel
    ) {
    }
}
