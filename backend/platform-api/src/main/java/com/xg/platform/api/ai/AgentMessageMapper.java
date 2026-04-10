package com.xg.platform.api.ai;

import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.MimeTypeUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgentMessageMapper {

    List<Message> prependSystem(String prompt, List<Message> messages) {
        List<Message> promptMessages = new ArrayList<>(messages.size() + 1);
        promptMessages.add(new SystemMessage(prompt));
        promptMessages.addAll(messages);
        return List.copyOf(promptMessages);
    }

    List<Message> toConversationMessages(List<MessageRecord> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }
        return recentMessages.stream()
                .map(this::toChatMessage)
                .toList();
    }

    List<Message> toConversationMessages(String providerId,
                                         AgentExecutionRequest request,
                                         List<AgentGraphMessage> graphMessages,
                                         String currentUserGraphMessageId) {
        if (graphMessages == null || graphMessages.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>(graphMessages.size());
        for (AgentGraphMessage graphMessage : graphMessages) {
            messages.add(toChatMessage(providerId, request, graphMessage, currentUserGraphMessageId));
        }
        return List.copyOf(messages);
    }

    UserMessage toCurrentUserMessage(String providerId, AgentExecutionRequest request) {
        if (request.inputImages() == null || request.inputImages().isEmpty()) {
            return new UserMessage(request.message());
        }
        if (!supportsVisionInput(providerId)) {
            throw new IllegalArgumentException("Provider " + providerId + " does not support image input in the current implementation");
        }
        List<Media> media = request.inputImages().stream()
                .map(this::toMedia)
                .toList();
        return UserMessage.builder()
                .text(request.message() == null ? "" : request.message())
                .media(media)
                .build();
    }

    private Message toChatMessage(MessageRecord messageRecord) {
        return switch (messageRecord.role()) {
            case USER -> new UserMessage(messageRecord.content());
            case ASSISTANT -> new AssistantMessage(messageRecord.content());
            case SYSTEM -> new SystemMessage(messageRecord.content());
        };
    }

    private Message toChatMessage(String providerId,
                                  AgentExecutionRequest request,
                                  AgentGraphMessage graphMessage,
                                  String currentUserGraphMessageId) {
        return switch (graphMessage.type()) {
            case USER -> toUserMessage(providerId, request, graphMessage, currentUserGraphMessageId);
            case ASSISTANT -> AssistantMessage.builder()
                    .content(graphMessage.content())
                    .properties(normalizeAssistantProperties(graphMessage.messageProperties()))
                    .toolCalls(graphMessage.toolCalls().stream()
                            .map(toolCall -> new AssistantMessage.ToolCall(
                                    toolCall.id(),
                                    "function",
                                    toolCall.name(),
                                    toolCall.arguments() == null ? "{}" : toolCall.arguments().toString()
                            ))
                            .toList())
                    .build();
            case TOOL -> ToolResponseMessage.builder()
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            graphMessage.toolCallId(),
                            graphMessage.toolName(),
                            toolResponseContent(graphMessage)
                    )))
                    .build();
        };
    }

    private Message toUserMessage(String providerId,
                                  AgentExecutionRequest request,
                                  AgentGraphMessage graphMessage,
                                  String currentUserGraphMessageId) {
        boolean currentUserMessage = currentUserGraphMessageId != null
                && currentUserGraphMessageId.equals(graphMessage.messageId());
        if (!currentUserMessage || request.inputImages() == null || request.inputImages().isEmpty()) {
            return new UserMessage(graphMessage.content());
        }
        return toCurrentUserMessage(providerId, request);
    }

    private Media toMedia(ThreadFileReference image) {
        Path imagePath = Path.of(image.absolutePath());
        if (!Files.exists(imagePath)) {
            throw new IllegalArgumentException("Image file not found: " + image.absolutePath());
        }
        return Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(image.contentType()))
                .data(new FileSystemResource(imagePath))
                .name(image.name())
                .build();
    }

    private boolean supportsVisionInput(String providerId) {
        return "gemini".equals(providerId) || "openai".equals(providerId);
    }

    private String toolResponseContent(AgentGraphMessage graphMessage) {
        Object modelContext = graphMessage.messageProperties().get("modelContext");
        if (modelContext instanceof String modelContextText && !modelContextText.isBlank()) {
            return modelContextText;
        }
        return graphMessage.content();
    }

    private Map<String, Object> normalizeAssistantProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Object thoughtSignatures = properties.get("thoughtSignatures");
        if (!(thoughtSignatures instanceof List<?> signatures) || signatures.isEmpty()) {
            return properties;
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
                    // Ignore malformed legacy values instead of crashing Gemini replay.
                }
            }
        }
        if (normalizedSignatures.isEmpty()) {
            return properties;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(properties);
        normalized.put("thoughtSignatures", List.copyOf(normalizedSignatures));
        return Map.copyOf(normalized);
    }
}
