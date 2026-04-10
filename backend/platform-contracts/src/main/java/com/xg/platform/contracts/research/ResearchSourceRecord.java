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

    public ResearchSourceRecord {
        supportingFindingIds = supportingFindingIds == null ? java.util.List.of() : java.util.List.copyOf(supportingFindingIds);
        citationIds = citationIds == null ? java.util.List.of() : java.util.List.copyOf(citationIds);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ResearchSourceRecord basic(String sourceId,
                                             ResearchSourceKind kind,
                                             String title,
                                             String uri,
                                             String locator,
                                             String snippet,
                                             String domain,
                                             String unitId) {
        return builder()
                .sourceId(sourceId)
                .kind(kind)
                .title(title)
                .uri(uri)
                .locator(locator)
                .snippet(snippet)
                .domain(domain)
                .unitId(unitId)
                .build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public ResearchSourceRecord withCitationLabel(String citationLabel) {
        return toBuilder().citationLabel(citationLabel).build();
    }

    public ResearchSourceRecord withEvidenceStatus(String evidenceStatus,
                                                   String verificationMethod,
                                                   Integer iterationNo,
                                                   String discoveryQuery) {
        return toBuilder()
                .iterationNo(iterationNo)
                .discoveryQuery(discoveryQuery)
                .evidenceStatus(evidenceStatus)
                .verificationMethod(verificationMethod)
                .build();
    }

    public ResearchSourceRecord withLinks(java.util.List<String> supportingFindingIds,
                                          java.util.List<String> citationIds,
                                          String anchorText) {
        return toBuilder()
                .supportingFindingIds(supportingFindingIds)
                .citationIds(citationIds)
                .anchorText(anchorText)
                .build();
    }

    public static final class Builder {
        private String sourceId;
        private ResearchSourceKind kind;
        private String title;
        private String uri;
        private String locator;
        private String snippet;
        private String domain;
        private String unitId;
        private String citationLabel;
        private Integer iterationNo;
        private String discoveryQuery;
        private String evidenceStatus;
        private String verificationMethod;
        private java.util.List<String> supportingFindingIds = java.util.List.of();
        private java.util.List<String> citationIds = java.util.List.of();
        private String anchorText;

        private Builder() {
        }

        private Builder(ResearchSourceRecord record) {
            this.sourceId = record.sourceId();
            this.kind = record.kind();
            this.title = record.title();
            this.uri = record.uri();
            this.locator = record.locator();
            this.snippet = record.snippet();
            this.domain = record.domain();
            this.unitId = record.unitId();
            this.citationLabel = record.citationLabel();
            this.iterationNo = record.iterationNo();
            this.discoveryQuery = record.discoveryQuery();
            this.evidenceStatus = record.evidenceStatus();
            this.verificationMethod = record.verificationMethod();
            this.supportingFindingIds = record.supportingFindingIds();
            this.citationIds = record.citationIds();
            this.anchorText = record.anchorText();
        }

        public Builder sourceId(String sourceId) { this.sourceId = sourceId; return this; }
        public Builder kind(ResearchSourceKind kind) { this.kind = kind; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder uri(String uri) { this.uri = uri; return this; }
        public Builder locator(String locator) { this.locator = locator; return this; }
        public Builder snippet(String snippet) { this.snippet = snippet; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder unitId(String unitId) { this.unitId = unitId; return this; }
        public Builder citationLabel(String citationLabel) { this.citationLabel = citationLabel; return this; }
        public Builder iterationNo(Integer iterationNo) { this.iterationNo = iterationNo; return this; }
        public Builder discoveryQuery(String discoveryQuery) { this.discoveryQuery = discoveryQuery; return this; }
        public Builder evidenceStatus(String evidenceStatus) { this.evidenceStatus = evidenceStatus; return this; }
        public Builder verificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; return this; }
        public Builder supportingFindingIds(java.util.List<String> supportingFindingIds) { this.supportingFindingIds = supportingFindingIds; return this; }
        public Builder citationIds(java.util.List<String> citationIds) { this.citationIds = citationIds; return this; }
        public Builder anchorText(String anchorText) { this.anchorText = anchorText; return this; }

        public ResearchSourceRecord build() {
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
    }
}
