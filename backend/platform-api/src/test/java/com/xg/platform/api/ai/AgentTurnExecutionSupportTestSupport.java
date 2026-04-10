package com.xg.platform.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentModelStep;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;
import com.xg.platform.tooling.domain.ToolGroup;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class AgentTurnExecutionSupportTestSupport {

    private AgentTurnExecutionSupportTestSupport() {
    }

    static SpringAiAgentTurnExecutionSupport createLegacySupport(ChatModel chatModel) {
        return createLegacySupport(chatModel, "test-model");
    }

    static SpringAiAgentTurnExecutionSupport createLegacySupport(ChatModel chatModel, String model) {
        return new SpringAiAgentTurnExecutionSupport(
                "gemini",
                resolver(model, chatModel),
                new UnsupportedToolService(),
                false,
                false,
                false
        );
    }

    static ChatClientAgentTurnExecutionSupport createChatClientSupport(ChatModel chatModel) {
        return createChatClientSupport(chatModel, "test-model");
    }

    static ChatClientAgentTurnExecutionSupport createChatClientSupport(ChatModel chatModel, String model) {
        return new ChatClientAgentTurnExecutionSupport(
                "gemini",
                resolver(model, chatModel),
                new UnsupportedToolService(),
                false,
                false,
                false
        );
    }

    static AgentExecutionRequest sampleRequest() {
        return sampleRequest(List.of());
    }

    static AgentExecutionRequest sampleRequest(List<ThreadFileReference> inputImages) {
        return sampleRequest("gemini", inputImages);
    }

    static AgentExecutionRequest sampleRequest(String providerId, List<ThreadFileReference> inputImages) {
        return AgentExecutionRequest.builder()
                .userId("user-1")
                .threadId("thread-1")
                .runId("run-1")
                .message("Say hello")
                .providerId(providerId)
                .requestedCapabilities(List.of())
                .skillIds(List.of())
                .skillSelectionMode("auto")
                .artifacts(List.of())
                .uploadedFiles(List.of())
                .inputImages(inputImages)
                .recentMessages(List.of())
                .sessionSummary("")
                .longTermMemory("")
                .build();
    }

    static ToolDescriptor sampleTool() {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        return new ToolDescriptor("web_search", "Search the web", objectMapper.createObjectNode(), ToolGroup.SEARCH, "builtin");
    }

    static ObjectNode resultNode(ObjectMapper objectMapper, String title, String url) {
        return objectMapper.createObjectNode()
                .put("title", title)
                .put("url", url);
    }

    static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    static ChatResponse response(String text, Map<String, Object> metadata) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content(text)
                .properties(metadata)
                .build())));
    }

    static ChatResponse multiGenerationResponse(List<AssistantMessage> messages) {
        return new ChatResponse(messages.stream().map(Generation::new).toList());
    }

    static ChatResponse responseWithToolCall(String text, String toolCallId, String toolName, String arguments) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content(text)
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        toolCallId,
                        "function",
                        toolName,
                        arguments
                )))
                .build())));
    }

    static ProviderClientResolver resolver(String model, ChatModel chatModel) {
        return (userId, requestedProviderId) -> new ProviderClientResolver.ResolvedProviderClient("gemini", model, chatModel);
    }

    static ProviderClientResolver resolver(Map<String, ProviderClientResolver.ResolvedProviderClient> providers) {
        Map<String, ProviderClientResolver.ResolvedProviderClient> normalized = new LinkedHashMap<>();
        providers.forEach((providerId, resolved) -> normalized.put(providerId.toLowerCase(), resolved));
        return (userId, requestedProviderId) -> {
            ProviderClientResolver.ResolvedProviderClient resolved = normalized.get(requestedProviderId.toLowerCase());
            if (resolved == null) {
                throw new IllegalArgumentException("Unknown provider: " + requestedProviderId);
            }
            return resolved;
        };
    }

    static final class UnsupportedToolService implements AgentToolService {

        private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

        @Override
        public List<ToolDescriptor> listAvailableTools(String userId) {
            return List.of(new ToolDescriptor("web_search", "Search the web", objectMapper.createObjectNode(), ToolGroup.SEARCH, "builtin"));
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    static class StreamingFakeChatModel implements ChatModel {

        private final List<String> chunks;
        private final String finalText;

        StreamingFakeChatModel(List<String> chunks, String finalText) {
            this.chunks = chunks;
            this.finalText = finalText;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return response(finalText);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.fromIterable(chunks).map(AgentTurnExecutionSupportTestSupport::response);
        }
    }

    static final class StreamingResponseChatModel implements ChatModel {

        private final List<ChatResponse> streamedResponses;
        private final ChatResponse finalResponse;

        StreamingResponseChatModel(List<ChatResponse> streamedResponses, ChatResponse finalResponse) {
            this.streamedResponses = streamedResponses;
            this.finalResponse = finalResponse;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return finalResponse;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.fromIterable(streamedResponses);
        }
    }

    static final class FallbackFakeChatModel implements ChatModel {

        private final String finalText;

        FallbackFakeChatModel(String finalText) {
            this.finalText = finalText;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return response(finalText);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.error(new IllegalStateException("stream not available"));
        }
    }

    static final class FallbackResponseChatModel implements ChatModel {

        private final ChatResponse response;

        FallbackResponseChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return response;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.error(new IllegalStateException("stream not available"));
        }
    }

    static class TrackingChatModel implements ChatModel {

        private final List<String> chunks;
        private final String finalText;
        private final AtomicInteger streamCalls = new AtomicInteger();
        private final AtomicInteger callCalls = new AtomicInteger();
        private GoogleGenAiChatOptions lastGoogleOptions;
        private Prompt lastPrompt;

        TrackingChatModel(List<String> chunks, String finalText) {
            this.chunks = chunks;
            this.finalText = finalText;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            callCalls.incrementAndGet();
            captureOptions(prompt);
            return response(finalText);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            streamCalls.incrementAndGet();
            captureOptions(prompt);
            return Flux.fromIterable(chunks).map(AgentTurnExecutionSupportTestSupport::response);
        }

        int streamCalls() {
            return streamCalls.get();
        }

        int callCalls() {
            return callCalls.get();
        }

        GoogleGenAiChatOptions lastGoogleOptions() {
            return lastGoogleOptions;
        }

        Prompt lastPrompt() {
            return lastPrompt;
        }

        protected void captureOptions(Prompt prompt) {
            lastPrompt = prompt;
            if (prompt.getOptions() instanceof GoogleGenAiChatOptions googleOptions) {
                lastGoogleOptions = googleOptions;
            }
        }
    }

    static final class RetryOnMediaChatModel extends TrackingChatModel {

        private final String finalText;

        RetryOnMediaChatModel(String finalText) {
            super(List.of(), finalText);
            this.finalText = finalText;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            captureOptions(prompt);
            if (containsMedia(prompt)) {
                throw new IllegalStateException("model does not support image media");
            }
            return response(finalText);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            captureOptions(prompt);
            if (containsMedia(prompt)) {
                return Flux.error(new IllegalStateException("unsupported image input"));
            }
            return Flux.error(new IllegalStateException("stream not available"));
        }

        private boolean containsMedia(Prompt prompt) {
            return prompt.getInstructions().stream()
                    .filter(UserMessage.class::isInstance)
                    .map(UserMessage.class::cast)
                    .anyMatch(message -> message.getMedia() != null && !message.getMedia().isEmpty());
        }
    }

    static final class ToolCallingChatModel implements ChatModel {

        private final ChatResponse response;

        ToolCallingChatModel(String text, String toolCallId, String toolName, String arguments) {
            this.response = responseWithToolCall(text, toolCallId, toolName, arguments);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return response;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.fromIterable(List.of(response));
        }
    }

    static final class RecordingEmitter implements AgentOutputEmitter {

        private final List<String> text = new ArrayList<>();
        private final List<RunEventType> eventTypes = new ArrayList<>();
        private final List<Map<String, String>> eventPayloads = new ArrayList<>();

        @Override
        public void emitText(String delta) {
            text.add(delta);
        }

        @Override
        public void emitEvent(RunEventType eventType, Object payload) {
            eventTypes.add(eventType);
            if (payload instanceof Map<?, ?> rawPayload) {
                java.util.LinkedHashMap<String, String> normalized = new java.util.LinkedHashMap<>();
                rawPayload.forEach((key, value) -> normalized.put(String.valueOf(key), String.valueOf(value)));
                eventPayloads.add(normalized);
            }
        }

        List<String> text() {
            return text;
        }

        List<RunEventType> eventTypes() {
            return eventTypes;
        }

        List<Map<String, String>> eventPayloads() {
            return eventPayloads;
        }
    }
}
