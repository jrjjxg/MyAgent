package com.xg.platform.contracts.model;

import java.util.List;

public record ModelProviderStatusResponse(
        boolean secretStorageAvailable,
        String defaultProviderId,
        List<ModelProviderStatusRecord> providers
) {
}
