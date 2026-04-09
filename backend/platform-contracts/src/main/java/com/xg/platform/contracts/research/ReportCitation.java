package com.xg.platform.contracts.research;

import java.io.Serializable;

public record ReportCitation(
        String citationLabel,
        String sourceId,
        ResearchSourceKind kind,
        String title,
        String uri,
        String locator,
        boolean usedInReport,
        int occurrenceCount,
        String citationId,
        String paragraphId,
        String blockId,
        String anchorText,
        java.util.List<String> supportingFindingIds
) implements Serializable {

    public ReportCitation(String citationLabel,
                          String sourceId,
                          ResearchSourceKind kind,
                          String title,
                          String uri,
                          String locator,
                          boolean usedInReport,
                          int occurrenceCount) {
        this(citationLabel, sourceId, kind, title, uri, locator, usedInReport, occurrenceCount, citationLabel, null, null, null, java.util.List.of());
    }

    public ReportCitation withUsage(boolean usedInReport, int occurrenceCount) {
        return new ReportCitation(citationLabel, sourceId, kind, title, uri, locator, usedInReport, occurrenceCount, citationId, paragraphId, blockId, anchorText, supportingFindingIds);
    }

    public ReportCitation withPlacement(String paragraphId,
                                        String blockId,
                                        String anchorText,
                                        java.util.List<String> supportingFindingIds) {
        return new ReportCitation(
                citationLabel,
                sourceId,
                kind,
                title,
                uri,
                locator,
                usedInReport,
                occurrenceCount,
                citationId,
                paragraphId,
                blockId,
                anchorText,
                supportingFindingIds == null ? java.util.List.of() : java.util.List.copyOf(supportingFindingIds)
        );
    }
}
