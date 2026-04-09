package com.xg.platform.agent.core.application;

import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.contracts.message.MessageRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LlmConversationSummaryCompressor implements ConversationSummaryCompressor {

    private static final Logger logger = Logger.getLogger(LlmConversationSummaryCompressor.class.getName());

    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final String providerId;
    private final String model;
    private final int maxMessagesPerChunk;
    private final int maxCharsPerChunk;
    private final int maxSummaryWords;
    private final ConversationSummaryCompressor fallback;
    private final boolean logAgentFlow;

    public LlmConversationSummaryCompressor(AgentTurnExecutionSupport agentTurnExecutionSupport,
                                            String providerId,
                                            String model,
                                            int maxMessagesPerChunk,
                                            int maxCharsPerChunk,
                                            int maxSummaryWords,
                                            ConversationSummaryCompressor fallback,
                                            boolean logAgentFlow) {
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.providerId = providerId;
        this.model = model;
        this.maxMessagesPerChunk = Math.max(1, maxMessagesPerChunk);
        this.maxCharsPerChunk = Math.max(400, maxCharsPerChunk);
        this.maxSummaryWords = Math.max(80, maxSummaryWords);
        this.fallback = fallback == null ? new SimpleConversationSummaryCompressor() : fallback;
        this.logAgentFlow = logAgentFlow;
    }

    @Override
    public String summarizeHistory(String userId, List<MessageRecord> historicalMessages) {
        if (historicalMessages == null || historicalMessages.isEmpty()) {
            return "";
        }
        String summary = "";
        for (List<MessageRecord> chunk : partition(historicalMessages)) {
            summary = compress(userId, summary, chunk);
        }
        return summary;
    }

    @Override
    public String extendSummary(String userId, String existingSummary, List<MessageRecord> newlyHistoricalMessages) {
        String summary = normalize(existingSummary);
        if (newlyHistoricalMessages == null || newlyHistoricalMessages.isEmpty()) {
            return summary;
        }
        for (List<MessageRecord> chunk : partition(newlyHistoricalMessages)) {
            summary = compress(userId, summary, chunk);
        }
        return summary;
    }

    private String compress(String userId, String existingSummary, List<MessageRecord> chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return normalize(existingSummary);
        }
        if (agentTurnExecutionSupport == null || providerId == null || providerId.isBlank()) {
            return fallback.extendSummary(userId, existingSummary, chunk);
        }
        try {
            String response = agentTurnExecutionSupport.runTextTurn(
                    userId,
                    providerId,
                    model,
                    systemPrompt(),
                    userPrompt(existingSummary, chunk)
            );
            String normalized = normalizeModelSummary(response);
            if (!normalized.isBlank()) {
                logFlow(() -> "conversation summary updated provider=" + providerId
                        + " model=" + blankFallback(model, "<default>")
                        + " chunkMessages=" + chunk.size()
                        + " summaryChars=" + normalized.length());
                return normalized;
            }
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Conversation summary compression failed, falling back to simple summary", exception);
        }
        return fallback.extendSummary(userId, existingSummary, chunk);
    }

    private String systemPrompt() {
        return """
                You maintain a rolling compressed summary of earlier conversation context for an AI agent.
                Recent raw messages will be preserved separately, so summarize only the durable context from the provided history.

                Return concise Markdown using only these optional headings when relevant:
                Goal
                Constraints
                Confirmed Facts
                Decisions
                Open Questions

                Rules:
                - Use flat "- " bullets under headings.
                - Keep only information useful for later turns.
                - Preserve explicit user preferences, constraints, identifiers, file paths, URLs, dates, model names, task states, and confirmed decisions when they may matter later.
                - Merge the existing summary with the new conversation chunk and remove duplicates.
                - If new information supersedes older information, keep only the latest version.
                - Omit greetings, filler, transient acknowledgements, and raw tool chatter.
                - Do not mention every turn or say "the conversation".
                - Keep the final summary under %d words.
                - If nothing useful remains, return exactly: none
                """.formatted(maxSummaryWords);
    }

    private String userPrompt(String existingSummary, List<MessageRecord> chunk) {
        return """
                Existing summary:
                %s

                New conversation chunk to compress:
                %s

                Return the updated rolling summary only.
                """.formatted(
                normalize(existingSummary).isBlank() ? "none" : normalize(existingSummary),
                renderChunk(chunk)
        );
    }

    private String renderChunk(List<MessageRecord> chunk) {
        return chunk.stream()
                .map(message -> "- " + message.role().name() + ": " + abbreviate(message.content()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
    }

    private List<List<MessageRecord>> partition(List<MessageRecord> messages) {
        List<List<MessageRecord>> chunks = new ArrayList<>();
        List<MessageRecord> current = new ArrayList<>();
        int currentChars = 0;
        for (MessageRecord message : messages) {
            int messageChars = estimateChars(message);
            boolean shouldFlush = !current.isEmpty()
                    && (current.size() >= maxMessagesPerChunk || currentChars + messageChars > maxCharsPerChunk);
            if (shouldFlush) {
                chunks.add(List.copyOf(current));
                current.clear();
                currentChars = 0;
            }
            current.add(message);
            currentChars += messageChars;
        }
        if (!current.isEmpty()) {
            chunks.add(List.copyOf(current));
        }
        return List.copyOf(chunks);
    }

    private int estimateChars(MessageRecord message) {
        return message.role().name().length() + abbreviate(message.content()).length() + 4;
    }

    private String abbreviate(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return normalized.length() > 500 ? normalized.substring(0, 500) + "..." : normalized;
    }

    private String normalizeModelSummary(String response) {
        String normalized = normalize(stripCodeFence(response));
        if ("none".equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private String stripCodeFence(String response) {
        String normalized = normalize(response);
        if (!normalized.startsWith("```")) {
            return normalized;
        }
        int firstLineEnd = normalized.indexOf('\n');
        int lastFence = normalized.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            return normalized;
        }
        return normalized.substring(firstLineEnd + 1, lastFence).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void logFlow(java.util.function.Supplier<String> supplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(supplier);
        }
    }

    int maxMessagesPerChunk() {
        return maxMessagesPerChunk;
    }

    int maxCharsPerChunk() {
        return maxCharsPerChunk;
    }
}
