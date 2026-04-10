package com.xg.platform.research.runtime;

import com.xg.platform.agent.core.ResearchUnitResult;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.research.ResearchEvidenceStatus;
import com.xg.platform.contracts.research.ResearchReportBlock;
import com.xg.platform.contracts.research.ResearchSourceKind;
import com.xg.platform.contracts.research.ResearchSourceRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResearchEvidenceSupport {

    private ResearchEvidenceSupport() {
    }

    public static List<ResearchSourceRecord> buildSourceManifest(List<ResearchUnitResult> unitResults) {
        Map<String, ResearchSourceRecord> uniqueSources = new LinkedHashMap<>();
        for (ResearchUnitResult unitResult : unitResults) {
            List<ResearchSourceRecord> sourceRecords = unitResult.sourceRecords() == null ? List.of() : unitResult.sourceRecords();
            for (ResearchSourceRecord sourceRecord : sourceRecords) {
                if (sourceRecord == null) {
                    continue;
                }
                ResearchSourceRecord normalized = normalizeRecord(sourceRecord);
                uniqueSources.putIfAbsent(normalized.sourceId(), normalized);
            }
        }
        Map<String, Integer> prefixCounters = new LinkedHashMap<>();
        List<ResearchSourceRecord> manifest = new ArrayList<>();
        for (ResearchSourceRecord sourceRecord : uniqueSources.values()) {
            String prefix = citationPrefix(sourceRecord.kind());
            int index = prefixCounters.merge(prefix, 1, Integer::sum);
            manifest.add(sourceRecord.withCitationLabel(prefix + index));
        }
        return List.copyOf(manifest);
    }

    public static List<ResearchSourceRecord> buildSourceManifestFromLedger(List<ResearchSourceRecord> sourceLedger) {
        Map<String, ResearchSourceRecord> uniqueSources = new LinkedHashMap<>();
        for (ResearchSourceRecord sourceRecord : sourceLedger) {
            if (sourceRecord == null) {
                continue;
            }
            ResearchSourceRecord normalized = normalizeRecord(sourceRecord);
            uniqueSources.putIfAbsent(normalized.sourceId(), normalized);
        }
        Map<String, Integer> prefixCounters = new LinkedHashMap<>();
        List<ResearchSourceRecord> manifest = new ArrayList<>();
        for (ResearchSourceRecord sourceRecord : uniqueSources.values()) {
            String prefix = citationPrefix(sourceRecord.kind());
            int index = prefixCounters.merge(prefix, 1, Integer::sum);
            manifest.add(sourceRecord.withCitationLabel(prefix + index));
        }
        return List.copyOf(manifest);
    }

    public static List<ReportCitation> buildReportCitations(List<ResearchUnitResult> unitResults) {
        List<ResearchSourceRecord> manifest = buildSourceManifest(unitResults);
        return buildReportCitationsFromSources(manifest);
    }

    public static List<ReportCitation> buildReportCitationsFromSources(List<ResearchSourceRecord> sources) {
        List<ResearchSourceRecord> manifest = buildSourceManifestFromLedger(sources);
        Set<String> fetchedUris = manifest.stream()
                .filter(sourceRecord -> isVerifiedOrCited(sourceRecord.evidenceStatus()))
                .filter(sourceRecord -> sourceRecord.kind() == ResearchSourceKind.WEB_PAGE && !isBlank(sourceRecord.uri()))
                .map(ResearchSourceRecord::uri)
                .collect(java.util.stream.Collectors.toSet());
        return manifest.stream()
                .filter(sourceRecord -> isVerifiedOrCited(sourceRecord.evidenceStatus()))
                .filter(sourceRecord -> sourceRecord.kind() != ResearchSourceKind.WEB_RESULT
                        || isBlank(sourceRecord.uri())
                        || !fetchedUris.contains(sourceRecord.uri()))
                .map(sourceRecord -> new ReportCitation(
                        sourceRecord.citationLabel(),
                        sourceRecord.sourceId(),
                        sourceRecord.kind(),
                        sourceRecord.title(),
                        sourceRecord.uri(),
                        sourceRecord.locator(),
                        false,
                        0,
                        sourceRecord.citationLabel(),
                        null,
                        null,
                        sourceRecord.anchorText(),
                        sourceRecord.supportingFindingIds()
                ))
                .toList();
    }

    public static List<ReportCitation> markCitationUsage(List<ReportCitation> citations, String report) {
        return markCitationUsage(citations, report, List.of());
    }

    public static List<ReportCitation> markCitationUsage(List<ReportCitation> citations,
                                                         String report,
                                                         List<ResearchReportBlock> blocks) {
        String normalizedReport = report == null ? "" : report;
        List<ReportCitation> usedCitations = new ArrayList<>();
        for (ReportCitation citation : citations) {
            String token = "[" + citation.citationLabel() + "]";
            int occurrences = countOccurrences(normalizedReport, token);
            ReportCitation updated = citation.withUsage(occurrences > 0, occurrences);
            String citationId = updated.citationId();
            ResearchReportBlock block = blocks.stream()
                    .filter(candidate -> candidate.citationIds().contains(citationId))
                    .findFirst()
                    .orElse(null);
            if (block != null) {
                updated = updated.withPlacement(block.paragraphId(), block.blockId(), updated.anchorText(), updated.supportingFindingIds());
            }
            usedCitations.add(updated);
        }
        return List.copyOf(usedCitations);
    }

    public static String ensureReportCitations(String report, List<ReportCitation> citations) {
        String normalizedReport = report == null ? "" : report.trim();
        if (citations.isEmpty()) {
            return normalizedReport;
        }
        if (markCitationUsage(citations, normalizedReport).stream().anyMatch(ReportCitation::usedInReport)) {
            return normalizedReport;
        }
        StringBuilder builder = new StringBuilder(normalizedReport);
        if (!normalizedReport.isBlank()) {
            builder.append(System.lineSeparator()).append(System.lineSeparator());
        }
        builder.append("## Sources").append(System.lineSeparator());
        for (ReportCitation citation : citations) {
            builder.append("- ").append(formatCitationLine(citation)).append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    public static String renderCitationInventory(List<ReportCitation> citations) {
        if (citations.isEmpty()) {
            return "- none";
        }
        StringBuilder builder = new StringBuilder();
        for (ReportCitation citation : citations) {
            builder.append("- ").append(formatCitationLine(citation));
            if (citation.kind() != null) {
                builder.append(" (").append(citation.kind().name()).append(")");
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    public static String formatCitationLine(ReportCitation citation) {
        StringBuilder builder = new StringBuilder()
                .append("[")
                .append(citation.citationLabel())
                .append("] ")
                .append(blankToFallback(citation.title(), citation.sourceId()));
        if (!isBlank(citation.locator())) {
            builder.append(", ").append(citation.locator());
        }
        if (!isBlank(citation.uri())) {
            builder.append(" - ").append(citation.uri());
        }
        return builder.toString();
    }

    public static ResearchSourceRecord normalizeRecord(ResearchSourceRecord sourceRecord) {
        String sourceId = isBlank(sourceRecord.sourceId())
                ? deriveSourceId(sourceRecord)
                : sourceRecord.sourceId().trim();
        return new ResearchSourceRecord(
                sourceId,
                sourceRecord.kind(),
                blankToFallback(sourceRecord.title(), sourceId),
                blankToNull(sourceRecord.uri()),
                blankToNull(sourceRecord.locator()),
                blankToNull(sourceRecord.snippet()),
                blankToNull(sourceRecord.domain()),
                blankToNull(sourceRecord.unitId()),
                sourceRecord.citationLabel(),
                sourceRecord.iterationNo(),
                blankToNull(sourceRecord.discoveryQuery()),
                blankToNull(sourceRecord.evidenceStatus()),
                blankToNull(sourceRecord.verificationMethod()),
                sourceRecord.supportingFindingIds(),
                sourceRecord.citationIds(),
                blankToNull(sourceRecord.anchorText())
        );
    }

    private static String deriveSourceId(ResearchSourceRecord sourceRecord) {
        StringBuilder builder = new StringBuilder(citationPrefix(sourceRecord.kind()).toLowerCase());
        builder.append(':');
        if (!isBlank(sourceRecord.uri())) {
            builder.append(sourceRecord.uri().trim());
        } else if (!isBlank(sourceRecord.title())) {
            builder.append(sourceRecord.title().trim());
        } else {
            builder.append("source");
        }
        if (!isBlank(sourceRecord.locator())) {
            builder.append('#').append(sourceRecord.locator().trim());
        }
        return builder.toString();
    }

    private static String citationPrefix(ResearchSourceKind kind) {
        if (kind == null) {
            return "S";
        }
        return switch (kind) {
            case WEB_RESULT, WEB_PAGE, WEATHER_REPORT -> "W";
            case DOCUMENT_CHUNK -> "D";
            case ARTIFACT -> "A";
            case MODEL_INFERENCE -> "M";
        };
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static String blankToFallback(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isVerifiedOrCited(String status) {
        return ResearchEvidenceStatus.CITED.name().equalsIgnoreCase(status)
                || ResearchEvidenceStatus.VERIFIED.name().equalsIgnoreCase(status);
    }
}
