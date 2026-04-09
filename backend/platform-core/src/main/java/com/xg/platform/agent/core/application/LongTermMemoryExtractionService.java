package com.xg.platform.agent.core.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.contracts.memory.CreateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryType;
import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;
import com.xg.platform.contracts.memory.UpdateLongTermMemoryRequest;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;
import com.xg.platform.runtime.LongTermMemoryJobRepository;
import com.xg.platform.runtime.LongTermMemoryRepository;
import com.xg.platform.runtime.MessageRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LongTermMemoryExtractionService {

    private static final Logger logger = Logger.getLogger(LongTermMemoryExtractionService.class.getName());

    private final MessageRepository messageRepository;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final LongTermMemoryJobRepository longTermMemoryJobRepository;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final ObjectMapper objectMapper;
    private final String extractionProviderId;
    private final String extractionModel;
    private final String extractionVersion;
    private final int maxContextMessages;
    private final boolean logAgentFlow;

    public LongTermMemoryExtractionService(MessageRepository messageRepository,
                                           LongTermMemoryRepository longTermMemoryRepository,
                                           LongTermMemoryJobRepository longTermMemoryJobRepository,
                                           AgentTurnExecutionSupport agentTurnExecutionSupport,
                                           ObjectMapper objectMapper,
                                           String extractionProviderId,
                                           String extractionModel,
                                           String extractionVersion,
                                           int maxContextMessages,
                                           boolean logAgentFlow) {
        this.messageRepository = messageRepository;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.longTermMemoryJobRepository = longTermMemoryJobRepository;
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.objectMapper = objectMapper;
        this.extractionProviderId = extractionProviderId;
        this.extractionModel = extractionModel;
        this.extractionVersion = extractionVersion;
        this.maxContextMessages = Math.max(4, maxContextMessages);
        this.logAgentFlow = logAgentFlow;
    }

    public ExtractionOutcome extractFromCompletedMessage(String userId, String threadId, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return new ExtractionOutcome(0, true, "missing_message_id");
        }
        Optional<MessageRecord> messageOptional = messageRepository.findById(userId, threadId, messageId);
        if (messageOptional.isEmpty()) {
            return new ExtractionOutcome(0, true, "message_not_found");
        }
        MessageRecord terminalMessage = messageOptional.get();
        if (terminalMessage.role() != MessageRole.ASSISTANT || terminalMessage.interactionMode() != InteractionMode.CHAT) {
            return new ExtractionOutcome(0, true, "unsupported_message");
        }
        List<MessageRecord> allMessages = messageRepository.listMessages(userId, threadId);
        String lastSucceededMessageId = longTermMemoryJobRepository.findLatestSucceeded(userId, threadId, extractionVersion)
                .map(MemoryExtractionJobRecord::messageId)
                .orElse(null);
        List<MessageRecord> contextMessages = messagesSinceLastExtraction(allMessages, lastSucceededMessageId, messageId);
        if (contextMessages.isEmpty()) {
            contextMessages = List.of(terminalMessage);
        }
        List<LongTermMemoryRecord> existingMemories = longTermMemoryRepository.listActive(userId);
        String response = agentTurnExecutionSupport.runTextTurn(
                userId,
                extractionProviderId,
                extractionModel,
                extractionPrompt(),
                renderUserMessage(existingMemories, contextMessages)
        );
        if (response == null || response.isBlank()) {
            return new ExtractionOutcome(0, true, "empty_model_response");
        }
        ParsedExtraction parsedExtraction = parseExtraction(response);
        int changed = applyExtraction(userId, threadId, messageId, parsedExtraction, existingMemories);
        if (changed == 0) {
            return new ExtractionOutcome(0, true, "no_actions");
        }
        int messageCount = contextMessages.size();
        logFlow(() -> "extractFromCompletedMessage thread=" + threadId
                + " messageId=" + messageId
                + " windowMessages=" + messageCount
                + " actions=" + changed);
        return new ExtractionOutcome(changed, false, "processed");
    }

    private String extractionPrompt() {
        return """
                You maintain structured long-term memory for a conversational agent.
                Read the existing memories and the recent conversation window, then return only JSON.

                Memory types:
                - PROFILE: durable user profile, standing preferences, expertise level, stable project context.
                - SEMANTIC: durable discrete facts about the user or their projects.
                - EPISODIC: notable but more time-bound events worth archiving for later retrieval.

                Merge rules:
                - Prefer updating an existing memory when it overlaps semantically with new information.
                - Use canonicalKey to normalize equivalent memories.
                - Do not create duplicates with the same memoryType and canonicalKey.
                - Delete older memories only when they are clearly superseded or redundant.
                - Ignore one-off requests, uncertain guesses, and assistant-only speculation.
                - Keep each content field concise and self-contained.

                Return strict JSON:
                {
                  "upserts": [
                    {
                      "memoryId": "existing-memory-id-or-empty",
                      "memoryType": "PROFILE|SEMANTIC|EPISODIC",
                      "canonicalKey": "lowercase.dot.key",
                      "title": "short label",
                      "content": "concise memory statement"
                    }
                  ],
                  "deleteMemoryIds": ["memory-id"]
                }
                """;
    }

    private String renderUserMessage(List<LongTermMemoryRecord> existingMemories, List<MessageRecord> contextMessages) {
        StringBuilder builder = new StringBuilder();
        builder.append("Existing active memories:\n");
        if (existingMemories.isEmpty()) {
            builder.append("- none\n");
        } else {
            for (LongTermMemoryRecord memory : existingMemories) {
                builder.append("- {")
                        .append("\"memoryId\":\"").append(memory.memoryId()).append("\", ")
                        .append("\"memoryType\":\"").append(memory.memoryType().name()).append("\", ")
                        .append("\"canonicalKey\":\"").append(nullSafe(memory.canonicalKey())).append("\", ")
                        .append("\"title\":\"").append(escape(memory.title())).append("\", ")
                        .append("\"content\":\"").append(escape(memory.content())).append("\"")
                        .append("}\n");
            }
        }
        builder.append('\n');
        builder.append("Conversation window since the last successful extraction:\n");
        for (MessageRecord message : contextMessages) {
            builder.append("- ")
                    .append(message.role().name())
                    .append(": ")
                    .append(message.content() == null ? "" : message.content().trim())
                    .append('\n');
        }
        return builder.toString();
    }

    private ParsedExtraction parseExtraction(String text) {
        try {
            JsonNode node = parseJsonObject(text);
            List<ExtractedMemoryUpsert> upserts = new ArrayList<>();
            if (node.path("upserts").isArray()) {
                for (JsonNode upsertNode : node.path("upserts")) {
                    LongTermMemoryType memoryType = memoryTypeOrDefault(textOrBlank(upsertNode.path("memoryType")));
                    String canonicalKey = normalizeCanonicalKey(textOrBlank(upsertNode.path("canonicalKey")));
                    String title = textOrBlank(upsertNode.path("title"));
                    String content = textOrBlank(upsertNode.path("content"));
                    if (!title.isBlank() && !content.isBlank()) {
                        upserts.add(new ExtractedMemoryUpsert(
                                textOrBlank(upsertNode.path("memoryId")),
                                memoryType,
                                canonicalKey == null ? fallbackCanonicalKey(memoryType, title) : canonicalKey,
                                title,
                                content
                        ));
                    }
                }
            }
            Set<String> deleteMemoryIds = new LinkedHashSet<>();
            if (node.path("deleteMemoryIds").isArray()) {
                for (JsonNode deleteNode : node.path("deleteMemoryIds")) {
                    String memoryId = textOrBlank(deleteNode);
                    if (!memoryId.isBlank()) {
                        deleteMemoryIds.add(memoryId);
                    }
                }
            }
            return new ParsedExtraction(List.copyOf(upserts), Set.copyOf(deleteMemoryIds));
        } catch (JsonProcessingException exception) {
            logFlow(() -> "extractFromCompletedMessage parse failed: " + exception.getMessage());
            return new ParsedExtraction(List.of(), Set.of());
        }
    }

    private int applyExtraction(String userId,
                                String threadId,
                                String messageId,
                                ParsedExtraction parsedExtraction,
                                List<LongTermMemoryRecord> existingMemories) {
        Map<String, LongTermMemoryRecord> activeById = new LinkedHashMap<>();
        Map<String, List<LongTermMemoryRecord>> activeByKey = new LinkedHashMap<>();
        for (LongTermMemoryRecord memory : existingMemories) {
            activeById.put(memory.memoryId(), memory);
            activeByKey.computeIfAbsent(key(memory.memoryType(), memory.canonicalKey()), ignored -> new ArrayList<>()).add(memory);
        }

        Map<String, ExtractedMemoryUpsert> dedupedUpserts = new LinkedHashMap<>();
        for (ExtractedMemoryUpsert upsert : parsedExtraction.upserts()) {
            dedupedUpserts.put(key(upsert.memoryType(), upsert.canonicalKey()), upsert);
        }

        int changed = 0;
        Set<String> retainedMemoryIds = new LinkedHashSet<>();
        for (ExtractedMemoryUpsert upsert : dedupedUpserts.values()) {
            LongTermMemoryRecord existing = resolveExistingMemory(userId, upsert, activeById, activeByKey);
            if (existing != null) {
                longTermMemoryRepository.update(userId, existing.memoryId(), new UpdateLongTermMemoryRequest(
                        upsert.memoryType(),
                        upsert.canonicalKey(),
                        upsert.title(),
                        upsert.content(),
                        threadId,
                        messageId,
                        null
                ));
                retainedMemoryIds.add(existing.memoryId());
                changed++;
                for (LongTermMemoryRecord duplicate : activeByKey.getOrDefault(key(upsert.memoryType(), upsert.canonicalKey()), List.of())) {
                    if (!duplicate.memoryId().equals(existing.memoryId())) {
                        longTermMemoryRepository.delete(userId, duplicate.memoryId());
                        changed++;
                    }
                }
            } else {
                LongTermMemoryRecord created = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                        upsert.memoryType(),
                        upsert.canonicalKey(),
                        upsert.title(),
                        upsert.content(),
                        threadId,
                        messageId,
                        null
                ));
                retainedMemoryIds.add(created.memoryId());
                changed++;
            }
        }

        for (String memoryId : parsedExtraction.deleteMemoryIds()) {
            if (!retainedMemoryIds.contains(memoryId) && activeById.containsKey(memoryId)) {
                longTermMemoryRepository.delete(userId, memoryId);
                changed++;
            }
        }
        return changed;
    }

    private LongTermMemoryRecord resolveExistingMemory(String userId,
                                                       ExtractedMemoryUpsert upsert,
                                                       Map<String, LongTermMemoryRecord> activeById,
                                                       Map<String, List<LongTermMemoryRecord>> activeByKey) {
        if (!upsert.memoryId().isBlank()) {
            LongTermMemoryRecord referenced = activeById.get(upsert.memoryId());
            if (referenced != null) {
                return referenced;
            }
        }
        List<LongTermMemoryRecord> byKey = activeByKey.getOrDefault(key(upsert.memoryType(), upsert.canonicalKey()), List.of());
        if (!byKey.isEmpty()) {
            return byKey.get(0);
        }
        return longTermMemoryRepository.findActiveByCanonicalKey(userId, upsert.memoryType(), upsert.canonicalKey()).orElse(null);
    }

    private List<MessageRecord> messagesSinceLastExtraction(List<MessageRecord> allMessages,
                                                            String lastSucceededMessageId,
                                                            String currentMessageId) {
        List<MessageRecord> window = new ArrayList<>();
        boolean started = lastSucceededMessageId == null
                || lastSucceededMessageId.isBlank()
                || allMessages.stream().noneMatch(message -> message.messageId().equals(lastSucceededMessageId));
        for (MessageRecord message : allMessages) {
            if (!started) {
                if (message.messageId().equals(lastSucceededMessageId)) {
                    started = true;
                }
                continue;
            }
            if (message.interactionMode() == InteractionMode.CHAT) {
                window.add(message);
            }
            if (message.messageId().equals(currentMessageId)) {
                break;
            }
        }
        if (window.size() <= maxContextMessages) {
            return List.copyOf(window);
        }
        return List.copyOf(window.subList(window.size() - maxContextMessages, window.size()));
    }

    private JsonNode parseJsonObject(String text) throws JsonProcessingException {
        String trimmed = text == null ? "" : text.trim();
        try {
            return objectMapper.readTree(trimmed);
        } catch (JsonProcessingException ignored) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return objectMapper.readTree(trimmed.substring(start, end + 1));
            }
            throw ignored;
        }
    }

    private LongTermMemoryType memoryTypeOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return LongTermMemoryType.SEMANTIC;
        }
        try {
            return LongTermMemoryType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return LongTermMemoryType.SEMANTIC;
        }
    }

    private String fallbackCanonicalKey(LongTermMemoryType memoryType, String title) {
        String normalizedTitle = normalizeCanonicalKey(title);
        String prefix = switch (memoryType) {
            case PROFILE -> "profile";
            case SEMANTIC -> "semantic";
            case EPISODIC -> "episode";
        };
        return normalizedTitle == null ? prefix : prefix + "." + normalizedTitle;
    }

    private String normalizeCanonicalKey(String value) {
        String trimmed = textOrBlank(value);
        if (trimmed.isBlank()) {
            return null;
        }
        String normalized = trimmed.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String textOrBlank(JsonNode node) {
        return node != null && node.isTextual() ? node.asText().trim() : "";
    }

    private String textOrBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private String key(LongTermMemoryType memoryType, String canonicalKey) {
        return memoryType.name() + "::" + canonicalKey;
    }

    private String escape(String value) {
        return nullSafe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private void logFlow(java.util.function.Supplier<String> supplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(supplier);
        }
    }

    private record ExtractedMemoryUpsert(
            String memoryId,
            LongTermMemoryType memoryType,
            String canonicalKey,
            String title,
            String content
    ) {
    }

    private record ParsedExtraction(List<ExtractedMemoryUpsert> upserts, Set<String> deleteMemoryIds) {
    }

    public record ExtractionOutcome(int processed, boolean skipped, String reason) {
    }
}
