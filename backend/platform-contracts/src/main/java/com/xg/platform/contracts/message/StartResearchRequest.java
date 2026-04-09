package com.xg.platform.contracts.message;

public record StartResearchRequest(
        String providerId,
        Integer draftRevision
) {
}
