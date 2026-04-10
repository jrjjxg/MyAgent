package com.xg.platform.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentGraphToolCall;
import com.xg.platform.agent.core.AgentModelStep;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.contracts.shared.event.RunEventType;
import org.springframework.ai.chat.messages.Message;
import com.xg.platform.tooling.domain.ToolDescriptor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class AbstractSpringAiAgentTurnExecutionSupport implements AgentTurnExecutionSupport {

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected final String defaultProviderId;
    protected final ProviderClientResolver providerClientResolver;
    protected final AgentMessageMapper messageMapper;
    protected final AgentToolCallbackFactory toolCallbackFactory;
    protected final AgentResponsePostProcessor responsePostProcessor;
    protected final AgentChatOptionsFactory chatOptionsFactory;
    protected final ImageInputOcrFallbackService imageInputOcrFallbackService;
    private final boolean logPrompts;
    private final boolean logAgentFlow;
    private final boolean logModelResponses;
    private final Map<String, Boolean> loggedSystemPromptKeys = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 2048;
        }
    });

    protected AbstractSpringAiAgentTurnExecutionSupport(String defaultProviderId,
                                                        ProviderClientResolver providerClientResolver,
                                                        AgentToolService agentToolService,
                                                        boolean logPrompts,
                                                        boolean logAgentFlow,
                                                        boolean logModelResponses) {
        this(
                defaultProviderId,
                providerClientResolver,
                new AgentMessageMapper(),
                new AgentToolCallbackFactory(agentToolService, logAgentFlow),
                new AgentResponsePostProcessor(),
                new AgentChatOptionsFactory(),
                logPrompts,
                logAgentFlow,
                logModelResponses
        );
    }

    protected AbstractSpringAiAgentTurnExecutionSupport(String defaultProviderId,
                                                        ProviderClientResolver providerClientResolver,
                                                        AgentMessageMapper messageMapper,
                                                        AgentToolCallbackFactory toolCallbackFactory,
                                                        AgentResponsePostProcessor responsePostProcessor,
                                                        AgentChatOptionsFactory chatOptionsFactory,
                                                        boolean logPrompts,
                                                        boolean logAgentFlow,
                                                        boolean logModelResponses) {
        this.defaultProviderId = defaultProviderId;
        this.providerClientResolver = providerClientResolver;
        this.messageMapper = messageMapper;
        this.toolCallbackFactory = toolCallbackFactory;
        this.responsePostProcessor = responsePostProcessor;
        this.chatOptionsFactory = chatOptionsFactory;
        this.imageInputOcrFallbackService = new ImageInputOcrFallbackService(providerClientResolver, chatOptionsFactory);
        this.logPrompts = logPrompts;
        this.logAgentFlow = logAgentFlow;
        this.logModelResponses = logModelResponses;
    }

    protected abstract Logger logger();

    @Override
    public String resolveProviderId(String requestedProviderId) {
        return resolveProviderId(null, requestedProviderId);
    }

    @Override
    public String resolveProviderId(String userId, String requestedProviderId) {
        return resolveProvider(userId, requestedProviderId).providerId();
    }

    protected ProviderClientResolver.ResolvedProviderClient resolveProvider(String userId, String requestedProviderId) {
        String providerId = requestedProviderId == null || requestedProviderId.isBlank()
                ? defaultProviderId
                : requestedProviderId.trim();
        return providerClientResolver.resolve(userId, providerId);
    }

    protected ChatOptions buildOptions(String providerId,
                                       String defaultModel,
                                       String modelOverride,
                                       boolean internalToolExecutionEnabled) {
        return chatOptionsFactory.build(providerId, defaultModel, modelOverride, internalToolExecutionEnabled);
    }

    protected ChatOptions buildOptions(String providerId,
                                       String defaultModel,
                                       String modelOverride,
                                       List<ToolCallback> toolCallbacks,
                                       boolean internalToolExecutionEnabled) {
        return chatOptionsFactory.build(providerId, defaultModel, modelOverride, toolCallbacks, internalToolExecutionEnabled);
    }

    protected ChatOptions buildOptions(String providerId,
                                       String defaultModel,
                                       String modelOverride,
                                       boolean internalToolExecutionEnabled,
                                       boolean includeGeminiThoughts) {
        return chatOptionsFactory.build(providerId, defaultModel, modelOverride, internalToolExecutionEnabled, includeGeminiThoughts);
    }

    protected ChatOptions buildOptions(String providerId,
                                       String defaultModel,
                                       String modelOverride,
                                       List<ToolCallback> toolCallbacks,
                                       boolean internalToolExecutionEnabled,
                                       boolean includeGeminiThoughts) {
        return chatOptionsFactory.build(providerId, defaultModel, modelOverride, toolCallbacks, internalToolExecutionEnabled, includeGeminiThoughts);
    }

    protected String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    protected JsonNode parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(arguments);
        } catch (Exception exception) {
            return JsonNodeFactory.instance.objectNode().put("raw", arguments);
        }
    }

    protected String requireText(String providerId,
                                 ChatResponse response,
                                 String operation) {
        String text = responseText(response);
        if (text == null) {
            logger().warning(() -> "empty model response provider=" + providerId
                    + " operation=" + operation
                    + " " + describeResponseStructure(response));
            throw new IllegalStateException("No response returned from provider " + providerId + " during " + operation);
        }
        logModelResult(() -> "model result provider=" + providerId
                + " operation=" + operation
                + " text=" + summarize(text));
        if (!text.isBlank()) {
            return text;
        }
        logger().warning(() -> "blank model response provider=" + providerId
                + " operation=" + operation
                + " " + describeResponseStructure(response));
        throw new IllegalStateException("Provider " + providerId + " returned an empty response during " + operation);
    }

    protected List<ToolCallback> createToolCallbacks(String providerId,
                                                     AgentExecutionRequest request,
                                                     List<ToolDescriptor> availableTools,
                                                     AgentOutputEmitter outputEmitter,
                                                     AgentSourceCollector sourceCollector) {
        return toolCallbackFactory.create(providerId, request, availableTools, outputEmitter, sourceCollector);
    }

    protected AssistantResponseParts streamAssistantResponse(String providerId,
                                                            String model,
                                                            AgentExecutionRequest request,
                                                            Supplier<Iterable<ChatResponse>> streamSupplier,
                                                            AgentOutputEmitter outputEmitter,
                                                            boolean emitVisibleText,
                                                            String operation) {
        StringBuilder visibleBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        List<AgentGraphToolCall> toolCalls = List.of();
        Map<String, Object> assistantProperties = Map.of();
        long startedAt = System.nanoTime();
        int chunkCount = 0;
        boolean firstChunkLogged = false;
        boolean thinkingStarted = false;
        logFlow(() -> operation + " start provider=" + providerId
                + " model=" + model
                + " thread=" + request.threadId()
                + " run=" + request.runId());
        try {
            for (ChatResponse partialResponse : streamSupplier.get()) {
                AssistantMessage output = partialResponse == null || partialResponse.getResult() == null
                        ? null
                        : partialResponse.getResult().getOutput();
                if (output == null) {
                    continue;
                }
                if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                    toolCalls = output.getToolCalls().stream()
                            .map(toolCall -> new AgentGraphToolCall(
                                    toolCall.id(),
                                    toolCall.name(),
                                    parseArguments(toolCall.arguments())
                            ))
                            .toList();
                }
                Map<String, Object> streamedAssistantProperties = preserveAssistantProperties(output.getMetadata());
                if (!streamedAssistantProperties.isEmpty()) {
                    assistantProperties = mergeAssistantProperties(assistantProperties, streamedAssistantProperties);
                }
                String delta = output.getText();
                if (delta == null || delta.isBlank()) {
                    continue;
                }
                chunkCount++;
                boolean thoughtDelta = isThoughtPart(output.getMetadata());
                if (thoughtDelta && !thinkingStarted) {
                    thinkingStarted = true;
                    outputEmitter.emitEvent(RunEventType.MODEL_THINKING_STARTED, Map.of(
                            "providerId", request.providerId(),
                            "runId", request.runId()
                    ));
                }
                if (!firstChunkLogged) {
                    firstChunkLogged = true;
                    int firstChunkLength = delta.length();
                    long firstChunkElapsedMs = elapsedMillis(startedAt);
                    logFlow(() -> operation + " first delta provider=" + providerId
                            + " model=" + model
                            + " thread=" + request.threadId()
                            + " run=" + request.runId()
                            + " elapsedMs=" + firstChunkElapsedMs
                            + " chars=" + firstChunkLength);
                }
                if (thoughtDelta) {
                    thinkingBuilder.append(delta);
                    outputEmitter.emitEvent(RunEventType.MODEL_THINKING_DELTA, Map.of("delta", delta));
                    continue;
                }
                visibleBuilder.append(delta);
                if (emitVisibleText) {
                    outputEmitter.emitText(delta);
                }
            }
        } catch (RuntimeException exception) {
            long failedElapsedMs = elapsedMillis(startedAt);
            logger().log(Level.WARNING, operation + " fallback provider=" + providerId
                    + " model=" + model
                    + " thread=" + request.threadId()
                    + " run=" + request.runId()
                    + " elapsedMs=" + failedElapsedMs
                    + " receivedChunks=" + chunkCount, exception);
            return null;
        }
        if (visibleBuilder.isEmpty() && thinkingBuilder.isEmpty() && toolCalls.isEmpty() && assistantProperties.isEmpty()) {
            long emptyElapsedMs = elapsedMillis(startedAt);
            int emptyChunks = chunkCount;
            logFlow(() -> operation + " completed without assistant output provider=" + providerId
                    + " model=" + model
                    + " thread=" + request.threadId()
                    + " run=" + request.runId()
                    + " elapsedMs=" + emptyElapsedMs
                    + " receivedChunks=" + emptyChunks);
            return null;
        }
        String streamedText = visibleBuilder.toString();
        String streamedThinking = thinkingBuilder.toString();
        if (!streamedThinking.isBlank()) {
            emitThinkingCompleted(outputEmitter, streamedThinking);
        }
        long elapsedMs = elapsedMillis(startedAt);
        int totalChars = streamedText.length();
        int completedChunks = chunkCount;
        List<AgentGraphToolCall> completedToolCalls = toolCalls;
        logFlow(() -> operation + " finished provider=" + providerId
                + " model=" + model
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " elapsedMs=" + elapsedMs
                + " receivedChunks=" + completedChunks
                + " chars=" + totalChars
                + " toolCalls=" + completedToolCalls.size());
        if (!streamedText.isBlank()) {
            logModelResult(() -> "model result provider=" + providerId
                    + " operation=" + operation
                    + " text=" + summarize(streamedText));
        }
        return new AssistantResponseParts(
                streamedText,
                streamedThinking,
                List.copyOf(completedToolCalls),
                assistantProperties
        );
    }

    protected AssistantResponseParts splitAssistantResponse(String providerId,
                                                            ChatResponse response,
                                                            String operation) {
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalStateException("No assistant message returned from provider " + providerId + " during " + operation);
        }
        StringBuilder visibleBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        List<AgentGraphToolCall> toolCalls = new ArrayList<>();
        Map<String, Object> assistantProperties = Map.of();
        boolean sawAssistantOutput = false;
        for (Generation generation : response.getResults()) {
            if (generation == null || generation.getOutput() == null) {
                continue;
            }
            AssistantMessage output = generation.getOutput();
            sawAssistantOutput = true;
            Map<String, Object> preservedProperties = preserveAssistantProperties(output.getMetadata());
            if (!preservedProperties.isEmpty()) {
                assistantProperties = mergeAssistantProperties(assistantProperties, preservedProperties);
            }
            if (output.getToolCalls() != null && !output.getToolCalls().isEmpty()) {
                toolCalls.addAll(output.getToolCalls().stream()
                        .map(toolCall -> new AgentGraphToolCall(
                                toolCall.id(),
                                toolCall.name(),
                                parseArguments(toolCall.arguments())
                        ))
                        .toList());
            }
            String text = output.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (isThoughtPart(output.getMetadata())) {
                thinkingBuilder.append(text);
            } else {
                visibleBuilder.append(text);
            }
        }
        if (!sawAssistantOutput) {
            throw new IllegalStateException("No assistant message returned from provider " + providerId + " during " + operation);
        }
        return new AssistantResponseParts(
                visibleBuilder.toString(),
                thinkingBuilder.toString(),
                List.copyOf(toolCalls),
                assistantProperties
        );
    }

    protected void emitThinkingTranscript(AgentExecutionRequest request,
                                          AgentOutputEmitter outputEmitter,
                                          String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        outputEmitter.emitEvent(RunEventType.MODEL_THINKING_STARTED, Map.of(
                "providerId", request.providerId(),
                "runId", request.runId()
        ));
        emitThinkingDeltas(outputEmitter, content);
        emitThinkingCompleted(outputEmitter, content);
    }

    protected void emitThinkingCompleted(AgentOutputEmitter outputEmitter, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "summary", summarize(content),
                "content", content
        );
        outputEmitter.emitEvent(RunEventType.MODEL_THINKING_COMPLETED, payload);
        outputEmitter.emitEvent(RunEventType.MODEL_THINKING, payload);
    }

    protected void emitThinkingDeltas(AgentOutputEmitter outputEmitter, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (int index = 0; index < text.length(); index += 180) {
            int end = Math.min(text.length(), index + 180);
            outputEmitter.emitEvent(RunEventType.MODEL_THINKING_DELTA, Map.of(
                    "delta", text.substring(index, end)
            ));
        }
    }

    protected long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    protected void emitTextChunks(String text, AgentOutputEmitter outputEmitter) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (int index = 0; index < text.length(); index += 180) {
            int end = Math.min(text.length(), index + 180);
            outputEmitter.emitText(text.substring(index, end));
        }
    }

    protected String summarize(String response) {
        String normalized = response == null ? "" : response.trim().replaceAll("\\s+", " ");
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    protected void logSystemPrompt(String operation,
                                   String providerId,
                                   String model,
                                   AgentExecutionRequest request,
                                   String prompt) {
        if (!logPrompts || !logger().isLoggable(Level.INFO) || prompt == null || prompt.isBlank()) {
            return;
        }
        String key = buildSystemPromptLogKey(operation, providerId, model, request, prompt);
        synchronized (loggedSystemPromptKeys) {
            if (loggedSystemPromptKeys.putIfAbsent(key, Boolean.TRUE) != null) {
                return;
            }
        }
        logger().info(() -> "system prompt provider=" + blankFallback(providerId, "<default>")
                + " model=" + blankFallback(model, "<default>")
                + " operation=" + operation
                + " thread=" + (request == null ? "" : blankFallback(request.threadId(), "<none>"))
                + " run=" + (request == null ? "" : blankFallback(request.runId(), "<none>"))
                + System.lineSeparator()
                + prompt);
    }

    protected void logSystemPrompt(String operation,
                                   String providerId,
                                   String model,
                                   String prompt) {
        if (!logPrompts || !logger().isLoggable(Level.INFO) || prompt == null || prompt.isBlank()) {
            return;
        }
        logger().info(() -> "system prompt provider=" + blankFallback(providerId, "<default>")
                + " model=" + blankFallback(model, "<default>")
                + " operation=" + operation
                + System.lineSeparator()
                + prompt);
    }

    protected void logConversationMessages(String operation,
                                           String providerId,
                                           String model,
                                           AgentExecutionRequest request,
                                           List<Message> messages) {
        if (!logAgentFlow || !logger().isLoggable(Level.INFO) || messages == null || messages.isEmpty()) {
            return;
        }
        long nonSystemCount = messages.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .count();
        if (nonSystemCount == 0) {
            return;
        }
        logFlow(() -> "conversation messages provider=" + blankFallback(providerId, "<default>")
                + " model=" + blankFallback(model, "<default>")
                + " operation=" + operation
                + " thread=" + (request == null ? "" : blankFallback(request.threadId(), "<none>"))
                + " run=" + (request == null ? "" : blankFallback(request.runId(), "<none>"))
                + " count=" + nonSystemCount);
        int index = 0;
        for (Message message : messages) {
            if (message instanceof SystemMessage) {
                continue;
            }
            index++;
            final int messageIndex = index;
            logFlow(() -> renderConversationMessage(operation, providerId, model, request, messageIndex, message));
        }
    }

    protected void logModelThinking(String providerId,
                                    String model,
                                    AgentExecutionRequest request,
                                    String hiddenReasoning,
                                    String operation) {
        if (hiddenReasoning == null || hiddenReasoning.isBlank()) {
            return;
        }
        logModelResult(() -> "model thinking provider=" + blankFallback(providerId, "<default>")
                + " model=" + blankFallback(model, "<default>")
                + " operation=" + operation
                + " thread=" + (request == null ? "" : blankFallback(request.threadId(), "<none>"))
                + " run=" + (request == null ? "" : blankFallback(request.runId(), "<none>"))
                + System.lineSeparator()
                + hiddenReasoning);
    }

    protected void logToolCalls(String providerId,
                                String model,
                                AgentExecutionRequest request,
                                List<AgentGraphToolCall> toolCalls,
                                String operation) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        for (AgentGraphToolCall toolCall : toolCalls) {
            logFlow(() -> "model selected tool provider=" + blankFallback(providerId, "<default>")
                    + " model=" + blankFallback(model, "<default>")
                    + " operation=" + operation
                    + " thread=" + (request == null ? "" : blankFallback(request.threadId(), "<none>"))
                    + " run=" + (request == null ? "" : blankFallback(request.runId(), "<none>"))
                    + " tool=" + toolCall.name()
                    + " toolCallId=" + blankFallback(toolCall.id(), "<none>")
                    + System.lineSeparator()
                    + (toolCall.arguments() == null ? "{}" : toolCall.arguments().toString()));
        }
    }

    protected void logFlow(Supplier<String> messageSupplier) {
        if (logAgentFlow && logger().isLoggable(Level.INFO)) {
            logger().info(messageSupplier);
        }
    }

    protected void logModelResult(Supplier<String> messageSupplier) {
        if (logModelResponses && logger().isLoggable(Level.INFO)) {
            logger().info(messageSupplier);
        }
    }

    protected boolean isThoughtPart(Map<String, Object> metadata) {
        return metadata != null && Boolean.TRUE.equals(metadata.get("isThought"));
    }

    protected Map<String, Object> mergeAssistantProperties(Map<String, Object> existing,
                                                           Map<String, Object> incoming) {
        if ((existing == null || existing.isEmpty()) && (incoming == null || incoming.isEmpty())) {
            return Map.of();
        }
        if (existing == null || existing.isEmpty()) {
            return incoming == null ? Map.of() : incoming;
        }
        if (incoming == null || incoming.isEmpty()) {
            return existing;
        }
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(incoming);
        return Map.copyOf(merged);
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    protected Map<String, Object> preserveAssistantProperties(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> preserved = new LinkedHashMap<>();
        if (metadata.containsKey("isThought")) {
            preserved.put("isThought", Boolean.TRUE.equals(metadata.get("isThought")));
        }
        Object thoughtSignatures = metadata.get("thoughtSignatures");
        if (thoughtSignatures instanceof List<?> signatures && !signatures.isEmpty()) {
            List<byte[]> copiedSignatures = new ArrayList<>();
            for (Object signature : signatures) {
                if (signature instanceof byte[] bytes) {
                    copiedSignatures.add(bytes.clone());
                }
            }
            if (!copiedSignatures.isEmpty()) {
                preserved.put("thoughtSignatures", List.copyOf(copiedSignatures));
            }
        }
        if (preserved.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(preserved);
    }

    protected record AssistantResponseParts(String visibleText,
                                            String thinkingText,
                                            List<AgentGraphToolCall> toolCalls,
                                            Map<String, Object> assistantProperties) {
    }

    private String buildSystemPromptLogKey(String operation,
                                           String providerId,
                                           String model,
                                           AgentExecutionRequest request,
                                           String prompt) {
        String runId = request == null ? "<no-run>" : blankFallback(request.runId(), "<no-run>");
        return runId
                + "|" + blankFallback(providerId, "<default>")
                + "|" + blankFallback(model, "<default>")
                + "|" + operation
                + "|" + prompt.hashCode();
    }

    private String renderConversationMessage(String operation,
                                             String providerId,
                                             String model,
                                             AgentExecutionRequest request,
                                             int index,
                                             Message message) {
        String header = "conversation message provider=" + blankFallback(providerId, "<default>")
                + " model=" + blankFallback(model, "<default>")
                + " operation=" + operation
                + " thread=" + (request == null ? "" : blankFallback(request.threadId(), "<none>"))
                + " run=" + (request == null ? "" : blankFallback(request.runId(), "<none>"))
                + " index=" + index
                + " role=" + message.getMessageType().name();
        if (message instanceof UserMessage userMessage) {
            return header
                    + " mediaCount=" + (userMessage.getMedia() == null ? 0 : userMessage.getMedia().size())
                    + System.lineSeparator()
                    + visibleText(userMessage.getText());
        }
        if (message instanceof AssistantMessage assistantMessage) {
            StringBuilder builder = new StringBuilder(header)
                    .append(" toolCalls=")
                    .append(assistantMessage.getToolCalls() == null ? 0 : assistantMessage.getToolCalls().size());
            if (assistantMessage.getText() != null && !assistantMessage.getText().isBlank()) {
                builder.append(System.lineSeparator()).append(assistantMessage.getText());
            }
            if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                    builder.append(System.lineSeparator())
                            .append("toolCall id=")
                            .append(blankFallback(toolCall.id(), "<none>"))
                            .append(" name=")
                            .append(blankFallback(toolCall.name(), "<none>"))
                            .append(System.lineSeparator())
                            .append(blankFallback(toolCall.arguments(), "{}"));
                }
            }
            return builder.toString();
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            StringBuilder builder = new StringBuilder(header)
                    .append(" responses=")
                    .append(toolResponseMessage.getResponses() == null ? 0 : toolResponseMessage.getResponses().size());
            if (toolResponseMessage.getResponses() != null && !toolResponseMessage.getResponses().isEmpty()) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    builder.append(System.lineSeparator())
                            .append("toolResponse id=")
                            .append(blankFallback(response.id(), "<none>"))
                            .append(" name=")
                            .append(blankFallback(response.name(), "<none>"))
                            .append(System.lineSeparator())
                            .append(visibleText(response.responseData()));
                }
            }
            return builder.toString();
        }
        return header + System.lineSeparator() + visibleText(message.getText());
    }

    private String visibleText(String text) {
        return text == null || text.isBlank() ? "<empty>" : text;
    }

    private String describeResponseStructure(ChatResponse response) {
        if (response == null) {
            return "response=null";
        }
        List<Generation> results = response.getResults();
        int resultCount = results == null ? 0 : results.size();
        Generation firstGeneration = response.getResult();
        if (firstGeneration == null) {
            return "response[resultCount=" + resultCount
                    + ", metadata=" + response.getMetadata()
                    + ", hasToolCalls=" + response.hasToolCalls()
                    + "]";
        }
        AssistantMessage output = firstGeneration.getOutput();
        if (output == null) {
            return "response[resultCount=" + resultCount
                    + ", metadata=" + response.getMetadata()
                    + ", generationMetadata=" + firstGeneration.getMetadata()
                    + ", output=null"
                    + ", hasToolCalls=" + response.hasToolCalls()
                    + "]";
        }
        String text = output.getText();
        int toolCallCount = output.getToolCalls() == null ? 0 : output.getToolCalls().size();
        int textLength = text == null ? -1 : text.length();
        return "response[resultCount=" + resultCount
                + ", metadata=" + response.getMetadata()
                + ", generationMetadata=" + firstGeneration.getMetadata()
                + ", outputTextLength=" + textLength
                + ", toolCallCount=" + toolCallCount
                + ", outputMetadata=" + output.getMetadata()
                + ", hasToolCalls=" + response.hasToolCalls()
                + "]";
    }
}
