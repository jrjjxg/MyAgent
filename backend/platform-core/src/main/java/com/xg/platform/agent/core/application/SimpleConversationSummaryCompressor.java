package com.xg.platform.agent.core.application;

import com.xg.platform.contracts.conversation.MessageRecord;

import java.util.List;

public class SimpleConversationSummaryCompressor implements ConversationSummaryCompressor {

    private static final int DEFAULT_MAX_LINES = 8;
    private static final int DEFAULT_MAX_CHARS_PER_LINE = 120;
    private static final int DEFAULT_MAX_TOTAL_CHARS = 900;

    private final int maxLines;
    private final int maxCharsPerLine;
    private final int maxTotalChars;

    public static SimpleConversationSummaryCompressor defaults() {
        return new SimpleConversationSummaryCompressor(
                DEFAULT_MAX_LINES,
                DEFAULT_MAX_CHARS_PER_LINE,
                DEFAULT_MAX_TOTAL_CHARS
        );
    }

    public SimpleConversationSummaryCompressor(int maxLines,
                                               int maxCharsPerLine,
                                               int maxTotalChars) {
        this.maxLines = Math.max(1, maxLines);
        this.maxCharsPerLine = Math.max(40, maxCharsPerLine);
        this.maxTotalChars = Math.max(120, maxTotalChars);
    }

    @Override
    public String summarizeHistory(String userId, List<MessageRecord> historicalMessages) {
        if (historicalMessages == null || historicalMessages.isEmpty()) {
            return "";
        }
        int fromIndex = Math.max(0, historicalMessages.size() - maxLines);
        String summary = historicalMessages.subList(fromIndex, historicalMessages.size()).stream()
                .map(message -> message.role().name() + ": " + abbreviate(message.content()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");
        return trimToBudget(summary);
    }

    @Override
    public String extendSummary(String userId, String existingSummary, List<MessageRecord> newlyHistoricalMessages) {
        String normalizedExisting = normalize(existingSummary);
        String addition = summarizeHistory(userId, newlyHistoricalMessages);
        if (normalizedExisting.isBlank()) {
            return addition;
        }
        if (addition.isBlank()) {
            return normalizedExisting;
        }
        return trimToBudget(normalizedExisting + System.lineSeparator() + addition);
    }

    private String normalize(String summary) {
        return summary == null ? "" : summary.trim();
    }

    private String trimToBudget(String summary) {
        if (summary.length() <= maxTotalChars) {
            return summary;
        }
        return summary.substring(summary.length() - maxTotalChars).trim();
    }

    private String abbreviate(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return normalized.length() > maxCharsPerLine
                ? normalized.substring(0, maxCharsPerLine) + "..."
                : normalized;
    }
}
