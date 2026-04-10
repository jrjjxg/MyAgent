package com.xg.platform.contracts.shared.model;

import java.util.List;

public record ModelProviderStatusResponse(
        boolean secretStorageAvailable,
        String defaultProviderId,
        List<ModelProviderStatusRecord> providers
) {
}
