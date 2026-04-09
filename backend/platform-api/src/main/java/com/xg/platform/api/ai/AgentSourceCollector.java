package com.xg.platform.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.xg.platform.contracts.research.ResearchSourceKind;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgentSourceCollector {

    private final Map<String, ReferencedSource> sourcesByUrl = new LinkedHashMap<>();

    void capture(String toolName, JsonNode output) {
        if ("web_search".equals(toolName)) {
            for (JsonNode resultNode : output.path("results")) {
                String url = resultNode.path("url").asText("").trim();
                if (url.isBlank()) {
                    continue;
                }
                add(new ReferencedSource(
                        ResearchSourceKind.WEB_RESULT,
                        resultNode.path("title").asText(url).trim(),
                        url,
                        domainOf(url)
                ));
            }
            return;
        }
        if ("web_fetch".equals(toolName)) {
            String url = output.path("url").asText("").trim();
            if (url.isBlank()) {
                return;
            }
            add(new ReferencedSource(
                    ResearchSourceKind.WEB_PAGE,
                    output.path("title").asText(url).trim(),
                    url,
                    domainOf(url)
            ));
            return;
        }
        if ("weather".equals(toolName)) {
            JsonNode source = output.path("source");
            String url = source.path("url").asText("").trim();
            if (url.isBlank()) {
                return;
            }
            add(new ReferencedSource(
                    ResearchSourceKind.WEATHER_REPORT,
                    source.path("title").asText("Weather Data").trim(),
                    url,
                    source.path("domain").asText("wttr.in").trim()
            ));
        }
    }

    List<ReferencedSource> sources() {
        List<ReferencedSource> values = List.copyOf(sourcesByUrl.values());
        List<ReferencedSource> fetchedPages = values.stream()
                .filter(source -> source.kind() == ResearchSourceKind.WEB_PAGE || source.kind() == ResearchSourceKind.WEATHER_REPORT)
                .toList();
        if (!fetchedPages.isEmpty()) {
            return fetchedPages;
        }
        return values.stream()
                .filter(source -> source.kind() == ResearchSourceKind.WEB_RESULT)
                .limit(3)
                .toList();
    }

    static String domainOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void add(ReferencedSource candidate) {
        ReferencedSource existing = sourcesByUrl.get(candidate.url());
        if (existing == null || existing.kind() == ResearchSourceKind.WEB_RESULT && candidate.kind() == ResearchSourceKind.WEB_PAGE) {
            ReferencedSource duplicateByTitleAndDomain = sourcesByUrl.values().stream()
                    .filter(source -> source.domain().equalsIgnoreCase(candidate.domain()))
                    .filter(source -> source.title().equalsIgnoreCase(candidate.title()))
                    .findFirst()
                    .orElse(null);
            if (duplicateByTitleAndDomain != null
                    && !(duplicateByTitleAndDomain.kind() == ResearchSourceKind.WEB_RESULT && candidate.kind() == ResearchSourceKind.WEB_PAGE)) {
                return;
            }
            sourcesByUrl.put(candidate.url(), candidate);
        }
    }

    record ReferencedSource(
            ResearchSourceKind kind,
            String title,
            String url,
            String domain
    ) {
    }
}
