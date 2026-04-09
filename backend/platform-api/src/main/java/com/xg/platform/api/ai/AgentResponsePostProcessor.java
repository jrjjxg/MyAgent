package com.xg.platform.api.ai;

import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.contracts.message.RunEventType;
import com.xg.platform.contracts.research.ResearchSourceKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    SanitizedResponse sanitizeVisibleResponse(String providerId, String response) {
        String normalized = response == null ? "" : response.trim();
        if (normalized.isBlank() || !"gemini".equalsIgnoreCase(providerId)) {
            return new SanitizedResponse(normalized, "");
        }
        List<String> blocks = splitBlocks(normalized).stream()
                .map(block -> block == null ? "" : block.trim())
                .filter(block -> !block.isBlank())
                .toList();
        if (blocks.isEmpty()) {
            return new SanitizedResponse("", "");
        }
        List<String> hiddenBlocks = new ArrayList<>();
        int visibleStart = 0;
        while (visibleStart < blocks.size()) {
            String current = blocks.get(visibleStart);
            String currentPlain = stripMarkdown(current);
            String next = visibleStart + 1 < blocks.size() ? blocks.get(visibleStart + 1) : null;
            String nextPlain = next == null ? "" : stripMarkdown(next);
            if (next != null && looksLikeLeadReasoningHeading(current, currentPlain, nextPlain)
                    && looksLikeLeadReasoningParagraph(nextPlain)) {
                hiddenBlocks.add(current);
                hiddenBlocks.add(next);
                visibleStart += 2;
                continue;
            }
            if (looksLikeLeadReasoningHeading(current, currentPlain, nextPlain)
                    || looksLikeLeadReasoningParagraph(currentPlain)) {
                hiddenBlocks.add(current);
                visibleStart++;
                continue;
            }
            break;
        }
        List<String> visibleBlocks = blocks.subList(Math.min(visibleStart, blocks.size()), blocks.size());
        if (visibleBlocks.isEmpty() || hiddenBlocks.isEmpty()) {
            return new SanitizedResponse(normalized, "");
        }
        return new SanitizedResponse(
                String.join(System.lineSeparator() + System.lineSeparator(), visibleBlocks).trim(),
                String.join(System.lineSeparator() + System.lineSeparator(), hiddenBlocks).trim()
        );
    }

    void emitModelThinking(AgentOutputEmitter outputEmitter, String hiddenReasoning) {
        if (hiddenReasoning == null || hiddenReasoning.isBlank()) {
            return;
        }
        outputEmitter.emitEvent(RunEventType.MODEL_THINKING_COMPLETED, Map.of(
                "summary", summarize(hiddenReasoning),
                "content", hiddenReasoning
        ));
        outputEmitter.emitEvent(RunEventType.MODEL_THINKING, Map.of(
                "summary", summarize(hiddenReasoning),
                "content", hiddenReasoning
        ));
    }

    private boolean looksLikeLeadReasoningHeading(String originalBlock,
                                                  String plainHeading,
                                                  String nextParagraph) {
        if (isMetaReasoningHeading(plainHeading) || isLikelyThoughtHeading(plainHeading, nextParagraph)) {
            return true;
        }
        String trimmed = originalBlock == null ? "" : originalBlock.trim();
        String plain = plainHeading == null ? "" : plainHeading.trim();
        if (plain.isBlank() || plain.length() > 120 || containsCjk(plain)) {
            return false;
        }
        return trimmed.startsWith("**")
                && trimmed.endsWith("**")
                && looksLikeLeadReasoningParagraph(nextParagraph);
    }

    private boolean looksLikeLeadReasoningParagraph(String text) {
        if (isMetaReasoningParagraph(text)) {
            return true;
        }
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || containsCjk(normalized)) {
            return false;
        }
        return normalized.startsWith("i'm ")
                || normalized.startsWith("i am ")
                || normalized.startsWith("i've ")
                || normalized.startsWith("i have ")
                || normalized.startsWith("my next step")
                || normalized.startsWith("the user is asking")
                || normalized.startsWith("i will ")
                || normalized.startsWith("i'll ");
    }

    private boolean containsCjk(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(index));
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitBlocks(String text) {
        return List.of(text.split("(?:\\r?\\n){2,}"));
    }

    private String stripMarkdown(String block) {
        String plain = block == null ? "" : block.trim();
        if (plain.startsWith("**") && plain.endsWith("**") && plain.length() > 4) {
            plain = plain.substring(2, plain.length() - 2).trim();
        }
        return plain.replace('\u201c', '"').replace('\u201d', '"');
    }

    private boolean isMetaReasoningHeading(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
        return containsAny(normalized,
                "defining the core task",
                "reviewing initial instructions",
                "summarizing user requests",
                "listing my capabilities",
                "outlining agent capabilities",
                "initiating the inquiry",
                "pinpointing the query",
                "investigating search limitations",
                "defining user s requests",
                "verifying background process",
                "interpreting the prompt",
                "analyzing skill constraints",
                "analysing skill constraints",
                "addressing user input",
                "investigating command failure",
                "refining product constraints",
                "interpreting the query",
                "interpreting asen",
                "assessing budget and knowledge",
                "analyzing asen s impact",
                "analysing asen s impact",
                "detailing asen s attributes",
                "clarifying the request",
                "planning the next step",
                "evaluating search results",
                "analyzing the user s intent",
                "analysing the user s intent",
                "gathering weather data",
                "synthesizing weather details",
                "synthesising weather details",
                "thinking",
                "reasoning",
                "tool planning",
                "planning",
                "using tools",
                "searching for sources");
    }

    private boolean isMetaReasoningParagraph(String text) {
        String normalized = text.toLowerCase(Locale.ROOT).trim();
        return containsAny(normalized,
                "i'm focusing on",
                "i am focusing on",
                "i've got the user's",
                "i have got the user's",
                "my next step is",
                "this initial understanding shapes my response strategy",
                "i'm now detailing",
                "i am now detailing",
                "i'm now zeroing in on",
                "i am now zeroing in on",
                "i'm trying to understand",
                "i am trying to understand",
                "i'll need to adapt my approach",
                "i will need to adapt my approach",
                "i've distilled the user's interactions",
                "i have distilled the user's interactions",
                "i'm currently focused on",
                "i am currently focused on",
                "i've successfully identified",
                "i have successfully identified",
                "i'm currently dissecting",
                "i am currently dissecting",
                "i've homed in on",
                "i have homed in on",
                "okay, so i've",
                "okay, so i have",
                "okay, i've",
                "okay, i have",
                "i'm incorporating details",
                "i am incorporating details",
                "my draft is taking shape",
                "i am making sure to note",
                "i'm prioritizing the context",
                "i am prioritizing the context",
                "i will provide practical advice",
                "i will provide",
                "the user is asking",
                "the prompt,",
                "the target date",
                "web search results pointed me",
                "i have translated and synthesized",
                "i have translated and synthesised");
    }

    private boolean isLikelyThoughtHeading(String heading, String nextParagraph) {
        if (heading == null || heading.isBlank() || nextParagraph == null || nextParagraph.isBlank()) {
            return false;
        }
        if (!isMetaReasoningParagraph(nextParagraph)) {
            return false;
        }
        String trimmed = heading.trim();
        if (trimmed.length() > 80) {
            return false;
        }
        if (!trimmed.matches("[A-Za-z0-9\"'\\-\\s]+")) {
            return false;
        }
        if (trimmed.matches(".*[.!?:;].*")) {
            return false;
        }
        String[] words = trimmed.split("\\s+");
        return words.length >= 1 && words.length <= 8;
    }

    private boolean containsAny(String text, String... fragments) {
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
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

    private String summarize(String response) {
        String normalized = response == null ? "" : response.trim().replaceAll("\\s+", " ");
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    record SanitizedResponse(
            String visibleText,
            String hiddenReasoning
    ) {
    }
}
