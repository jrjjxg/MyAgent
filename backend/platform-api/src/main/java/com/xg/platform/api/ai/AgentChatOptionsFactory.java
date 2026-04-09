package com.xg.platform.api.ai;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

final class AgentChatOptionsFactory {

    ChatOptions build(String providerId,
                      String defaultModel,
                      String modelOverride,
                      boolean internalToolExecutionEnabled) {
        return build(providerId, defaultModel, modelOverride, List.of(), internalToolExecutionEnabled, false);
    }

    ChatOptions build(String providerId,
                      String defaultModel,
                      String modelOverride,
                      List<ToolCallback> toolCallbacks,
                      boolean internalToolExecutionEnabled) {
        return build(providerId, defaultModel, modelOverride, toolCallbacks, internalToolExecutionEnabled, false);
    }

    ChatOptions build(String providerId,
                      String defaultModel,
                      String modelOverride,
                      boolean internalToolExecutionEnabled,
                      boolean includeGeminiThoughts) {
        return build(providerId, defaultModel, modelOverride, List.of(), internalToolExecutionEnabled, includeGeminiThoughts);
    }

    ChatOptions build(String providerId,
                      String defaultModel,
                      String modelOverride,
                      List<ToolCallback> toolCallbacks,
                      boolean internalToolExecutionEnabled,
                      boolean includeGeminiThoughts) {
        String resolvedModel = modelOverride != null && !modelOverride.isBlank()
                ? modelOverride
                : defaultModel;
        List<ToolCallback> callbacks = toolCallbacks == null ? List.of() : List.copyOf(toolCallbacks);
        return switch (providerId) {
            case "openai" -> OpenAiChatOptions.builder()
                    .model(resolvedModel)
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(internalToolExecutionEnabled)
                    .build();
            case "deepseek" -> DeepSeekChatOptions.builder()
                    .model(resolvedModel)
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(internalToolExecutionEnabled)
                    .build();
            case "gemini" -> GoogleGenAiChatOptions.builder()
                    .model(resolvedModel)
                    .includeThoughts(includeGeminiThoughts)
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(internalToolExecutionEnabled)
                    .build();
            default -> throw new IllegalArgumentException("Unsupported model provider: " + providerId);
        };
    }
}
