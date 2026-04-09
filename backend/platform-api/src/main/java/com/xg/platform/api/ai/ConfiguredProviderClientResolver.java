package com.xg.platform.api.ai;

import com.xg.platform.api.model.ModelProviderConfigService;
import org.springframework.ai.model.tool.ToolCallingManager;

public class ConfiguredProviderClientResolver implements ProviderClientResolver {

    private final ModelProviderConfigService modelProviderConfigService;
    private final ToolCallingManager toolCallingManager;

    public ConfiguredProviderClientResolver(ModelProviderConfigService modelProviderConfigService,
                                            ToolCallingManager toolCallingManager) {
        this.modelProviderConfigService = modelProviderConfigService;
        this.toolCallingManager = toolCallingManager;
    }

    @Override
    public ResolvedProviderClient resolve(String userId, String requestedProviderId) {
        ModelProviderConfigService.ResolvedModelProvider resolved = modelProviderConfigService.resolveProvider(
                userId,
                requestedProviderId,
                toolCallingManager
        );
        return new ResolvedProviderClient(resolved.providerId(), resolved.model(), resolved.chatModel());
    }
}
