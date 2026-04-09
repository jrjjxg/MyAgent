package com.xg.platform.contracts.research;

import java.io.Serializable;

public record ResearchSourceRecord(
        String sourceId,
        ResearchSourceKind kind,
        String title,
        String uri,
        String locator,
        String snippet,
        String domain,
        String unitId,
        String citationLabel,
        Integer iterationNo,
        String discoveryQuery,
        String evidenceStatus,
        String verificationMethod,
        java.util.List<String> supportingFindingIds,
        java.util.List<String> citationIds,
        String anchorText
) implements Serializable {

    public ResearchSourceRecord(String sourceId,
                                ResearchSourceKind kind,
                                String title,
                                String uri,
                                String locator,
                                String snippet,
                                String domain,
                                String unitId) {
        this(sourceId, kind, title, uri, locator, snippet, domain, unitId, null);
    }

    public ResearchSourceRecord(String sourceId,
                                ResearchSourceKind kind,
                                String title,
                                String uri,
                                String locator,
                                String snippet,
                                String domain,
                                String unitId,
                                String citationLabel) {
        this(sourceId, kind, title, uri, locator, snippet, domain, unitId, citationLabel, null, null, null, null, java.util.List.of(), java.util.List.of(), null);
    }

    public ResearchSourceRecord withCitationLabel(String citationLabel) {
        return new ResearchSourceRecord(sourceId, kind, title, uri, locator, snippet, domain, unitId, citationLabel, iterationNo, discoveryQuery, evidenceStatus, verificationMethod, supportingFindingIds, citationIds, anchorText);
    }

    public ResearchSourceRecord withEvidenceStatus(String evidenceStatus,
                                                   String verificationMethod,
                                                   Integer iterationNo,
                                                   String discoveryQuery) {
        return new ResearchSourceRecord(
                sourceId,
                kind,
                title,
                uri,
                locator,
                snippet,
                domain,
                unitId,
                citationLabel,
                iterationNo,
                discoveryQuery,
                evidenceStatus,
                verificationMethod,
                supportingFindingIds,
                citationIds,
                anchorText
        );
    }

    public ResearchSourceRecord withLinks(java.util.List<String> supportingFindingIds,
                                          java.util.List<String> citationIds,
                                          String anchorText) {
        return new ResearchSourceRecord(
                sourceId,
                kind,
                title,
                uri,
                locator,
                snippet,
                domain,
                unitId,
                citationLabel,
                iterationNo,
                discoveryQuery,
                evidenceStatus,
                verificationMethod,
                supportingFindingIds == null ? java.util.List.of() : java.util.List.copyOf(supportingFindingIds),
                citationIds == null ? java.util.List.of() : java.util.List.copyOf(citationIds),
                anchorText
        );
    }
}
