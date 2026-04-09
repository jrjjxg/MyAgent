package com.xg.platform.agent.core;

import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public record AgentGraphMessage(
        String messageId,
        AgentGraphMessageType type,
        String content,
        List<AgentGraphToolCall> toolCalls,
        String toolName,
        String toolCallId,
        Map<String, Object> messageProperties
) implements Serializable {

    public AgentGraphMessage {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolName = toolName == null ? "" : toolName;
        toolCallId = toolCallId == null ? "" : toolCallId;
        messageProperties = normalizeMessageProperties(messageProperties);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public static AgentGraphMessage fromMessageRecord(MessageRecord messageRecord) {
        AgentGraphMessageType type = switch (messageRecord.role()) {
            case USER -> AgentGraphMessageType.USER;
            case ASSISTANT, SYSTEM -> AgentGraphMessageType.ASSISTANT;
        };
        return new AgentGraphMessage(
                messageRecord.messageId(),
                type,
                messageRecord.content(),
                List.of(),
                "",
                "",
                Map.of()
        );
    }

    public static AgentGraphMessage user(MessageRecord messageRecord) {
        if (messageRecord.role() != MessageRole.USER) {
            throw new IllegalArgumentException("Message role must be USER");
        }
        return fromMessageRecord(messageRecord);
    }

    public static AgentGraphMessage assistant(String messageId,
                                              String content,
                                              List<AgentGraphToolCall> toolCalls) {
        return assistant(messageId, content, toolCalls, Map.of());
    }

    public static AgentGraphMessage assistant(String messageId,
                                              String content,
                                              List<AgentGraphToolCall> toolCalls,
                                              Map<String, Object> messageProperties) {
        return new AgentGraphMessage(messageId, AgentGraphMessageType.ASSISTANT, content, toolCalls, "", "", messageProperties);
    }

    public static AgentGraphMessage tool(String messageId,
                                         String toolName,
                                         String toolCallId,
                                         String content) {
        return tool(messageId, toolName, toolCallId, content, Map.of());
    }

    public static AgentGraphMessage tool(String messageId,
                                         String toolName,
                                         String toolCallId,
                                         String content,
                                         Map<String, Object> messageProperties) {
        return new AgentGraphMessage(messageId, AgentGraphMessageType.TOOL, content, List.of(), toolName, toolCallId, messageProperties);
    }

    private static Map<String, Object> normalizeMessageProperties(Map<String, Object> messageProperties) {
        if (messageProperties == null || messageProperties.isEmpty()) {
            return Map.of();
        }
        Object thoughtSignatures = messageProperties.get("thoughtSignatures");
        if (!(thoughtSignatures instanceof List<?> signatures) || signatures.isEmpty()) {
            return Map.copyOf(messageProperties);
        }
        List<byte[]> normalizedSignatures = new ArrayList<>();
        for (Object signature : signatures) {
            if (signature instanceof byte[] bytes) {
                normalizedSignatures.add(bytes.clone());
                continue;
            }
            if (signature instanceof String encoded && !encoded.isBlank()) {
                try {
                    normalizedSignatures.add(Base64.getDecoder().decode(encoded));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed signature payloads instead of breaking turn replay.
                }
            }
        }
        if (normalizedSignatures.isEmpty()) {
            return Map.copyOf(messageProperties);
        }
        Map<String, Object> normalizedProperties = new LinkedHashMap<>(messageProperties);
        normalizedProperties.put("thoughtSignatures", List.copyOf(normalizedSignatures));
        return Map.copyOf(normalizedProperties);
    }
}
