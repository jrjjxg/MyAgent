package com.xg.platform.contracts.research;

public record StartResearchRequest(
        String providerId,
        Integer draftRevision
) {
}
