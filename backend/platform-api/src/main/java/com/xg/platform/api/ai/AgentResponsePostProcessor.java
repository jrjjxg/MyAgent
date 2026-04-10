package com.xg.platform.api.ai;

import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.contracts.research.ResearchSourceKind;

import java.util.List;
import java.util.Locale;

final class AgentResponsePostProcessor {

    String appendSourceAppendix(String response,
                                AgentSourceCollector sourceCollector,
                                AgentOutputEmitter outputEmitter) {
        String normalized = response == null ? "" : response.trim();
        List<AgentSourceCollector.ReferencedSource> sources = sourceCollector.sources();
        if (normalized.isBlank() || sources.isEmpty() || hasSourceAppendix(normalized)) {
            return normalized;
        }
        String appendix = renderSourceAppendix(sources);
        String finalText = normalized + System.lineSeparator() + System.lineSeparator() + appendix;
        if (outputEmitter != null) {
            outputEmitter.emitText(System.lineSeparator() + System.lineSeparator() + appendix);
        }
        return finalText;
    }

    private boolean hasSourceAppendix(String response) {
        String normalized = response.toLowerCase(Locale.ROOT);
        return normalized.contains("\n## sources")
                || normalized.contains("\n### sources")
                || normalized.contains("\n\u6765\u6e90");
    }

    private String renderSourceAppendix(List<AgentSourceCollector.ReferencedSource> sources) {
        StringBuilder builder = new StringBuilder("## Sources").append(System.lineSeparator());
        for (AgentSourceCollector.ReferencedSource source : sources) {
            builder.append("- [")
                    .append(renderSourceLabel(source.kind()))
                    .append("] ")
                    .append(source.title());
            if (!source.domain().isBlank()) {
                builder.append(" | ").append(source.domain());
            }
            builder.append(" - ").append(source.url()).append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private String renderSourceLabel(ResearchSourceKind kind) {
        return switch (kind) {
            case WEB_PAGE -> "Web Page";
            case WEATHER_REPORT -> "Weather Data";
            default -> "Search Result";
        };
    }
}
