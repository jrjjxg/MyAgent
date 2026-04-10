package com.xg.platform.api.ai;

import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentModelStep;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.tooling.domain.ToolDescriptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ChatClientAgentTurnExecutionSupport extends AbstractSpringAiAgentTurnExecutionSupport {

    private static final Logger logger = Logger.getLogger(ChatClientAgentTurnExecutionSupport.class.getName());

    public ChatClientAgentTurnExecutionSupport(String defaultProviderId,
                                               ProviderClientResolver providerClientResolver,
                                               AgentToolService agentToolService,
                                               boolean logPrompts,
                                               boolean logAgentFlow,
                                               boolean logModelResponses) {
        super(defaultProviderId, providerClientResolver, agentToolService, logPrompts, logAgentFlow, logModelResponses);
    }

    ChatClientAgentTurnExecutionSupport(String defaultProviderId,
                                        ProviderClientResolver providerClientResolver,
                                        AgentMessageMapper messageMapper,
                                        AgentToolCallbackFactory toolCallbackFactory,
                                        AgentResponsePostProcessor responsePostProcessor,
                                        AgentChatOptionsFactory chatOptionsFactory,
                                        boolean logPrompts,
                                        boolean logAgentFlow,
                                        boolean logModelResponses) {
        super(
                defaultProviderId,
                providerClientResolver,
                messageMapper,
                toolCallbackFactory,
                responsePostProcessor,
                chatOptionsFactory,
                logPrompts,
                logAgentFlow,
                logModelResponses
        );
    }

    @Override
    protected Logger logger() {
        return logger;
    }

    @Override
    public String runTextTurn(String providerId,
                              String modelOverride,
                              String prompt,
                              String userMessage) {
        return runTextTurn(null, providerId, modelOverride, prompt, userMessage);
    }

    @Override
    public String runTextTurn(String userId,
                              String providerId,
                              String modelOverride,
                              String prompt,
                              String userMessage) {
        ProviderClientResolver.ResolvedProviderClient resolvedProvider = resolveProvider(userId, providerId);
        String resolvedProviderId = resolvedProvider.providerId();
        logFlow(() -> "runTextTurn provider=" + resolvedProviderId
                + " promptChars=" + prompt.length()
                + " userMessage=" + summarize(userMessage));
        logSystemPrompt("text turn", resolvedProviderId, resolvedProvider.model(), prompt);
        logConversationMessages(
                "text turn",
                resolvedProviderId,
                resolvedProvider.model(),
                null,
                List.of(new org.springframework.ai.chat.messages.UserMessage(userMessage))
        );
        ChatResponse response = ChatClient.create(resolvedProvider.chatModel())
                .prompt()
                .system(prompt)
                .user(userMessage)
                .options(buildOptions(resolvedProviderId, resolvedProvider.model(), modelOverride, false))
                .call()
                .chatResponse();
        return requireText(resolvedProviderId, response, "text turn");
    }

    @Override
    public String runModelLoop(String providerId,
                               AgentExecutionRequest request,
                               String prompt,
                               List<ToolDescriptor> availableTools,
                               AgentOutputEmitter outputEmitter) {
        ProviderClientResolver.ResolvedProviderClient resolvedProvider = resolveProvider(request.userId(), providerId);
        String resolvedProviderId = resolvedProvider.providerId();
        AgentExecutionRequest preparedRequest = imageInputOcrFallbackService.prepareForProvider(resolvedProviderId, request);
        try {
            return runModelLoopInternal(resolvedProvider, resolvedProviderId, preparedRequest, prompt, availableTools, outputEmitter);
        } catch (RuntimeException exception) {
            if (!imageInputOcrFallbackService.shouldRetryWithOcr(exception, resolvedProviderId, request, preparedRequest)) {
                throw exception;
            }
            AgentExecutionRequest ocrRequest = imageInputOcrFallbackService.forceOcrFallback(resolvedProviderId, request);
            return runModelLoopInternal(resolvedProvider, resolvedProviderId, ocrRequest, prompt, availableTools, outputEmitter);
        }
    }

    private String runModelLoopInternal(ProviderClientResolver.ResolvedProviderClient resolvedProvider,
                                        String resolvedProviderId,
                                        AgentExecutionRequest request,
                                        String prompt,
                                        List<ToolDescriptor> availableTools,
                                        AgentOutputEmitter outputEmitter) {
        List<Message> conversationMessages = new ArrayList<>(messageMapper.toConversationMessages(request.recentMessages()));
        conversationMessages.add(messageMapper.toCurrentUserMessage(resolvedProviderId, request));
        List<Message> promptMessages = messageMapper.prependSystem(prompt, conversationMessages);
        AgentSourceCollector sourceCollector = new AgentSourceCollector();
        List<ToolCallback> toolCallbacks = createToolCallbacks(
                resolvedProviderId,
                request,
                availableTools,
                outputEmitter,
                sourceCollector
        );
        ChatOptions options = buildOptions(
                resolvedProviderId,
                resolvedProvider.model(),
                null,
                !toolCallbacks.isEmpty(),
                "gemini".equalsIgnoreCase(resolvedProviderId)
        );
        logFlow(() -> "runModelLoop provider=" + resolvedProviderId
                + " model=" + resolvedProvider.model()
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " messages=" + conversationMessages.size()
                + " toolsEnabled=" + !toolCallbacks.isEmpty());
        logSystemPrompt("model loop", resolvedProviderId, resolvedProvider.model(), request, prompt);
        logConversationMessages("model loop", resolvedProviderId, resolvedProvider.model(), request, promptMessages);
        AssistantResponseParts streamedResponse = streamAssistantResponse(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                () -> newRequestSpec(resolvedProvider.chatModel(), promptMessages, options, toolCallbacks)
                        .stream()
                        .chatResponse()
                        .toIterable(),
                outputEmitter,
                true,
                "model loop stream"
        );
        if (streamedResponse != null) {
            logModelThinking(
                    resolvedProviderId,
                    resolvedProvider.model(),
                    request,
                    streamedResponse.thinkingText(),
                    "model loop stream"
            );
            String finalText = responsePostProcessor.appendSourceAppendix(
                    streamedResponse.visibleText(),
                    sourceCollector,
                    outputEmitter
            );
            return finalText;
        }
        long callStartedAt = System.nanoTime();
        logFlow(() -> "runModelLoop sync call start provider=" + resolvedProviderId
                + " model=" + resolvedProvider.model()
                + " thread=" + request.threadId()
                + " run=" + request.runId());
        ChatResponse response = newRequestSpec(resolvedProvider.chatModel(), promptMessages, options, toolCallbacks)
                .call()
                .chatResponse();
        long callElapsedMs = elapsedMillis(callStartedAt);
        logFlow(() -> "runModelLoop sync call finished provider=" + resolvedProviderId
                + " model=" + resolvedProvider.model()
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " elapsedMs=" + callElapsedMs);
        AssistantResponseParts responseParts = splitAssistantResponse(resolvedProviderId, response, "model loop");
        logModelThinking(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                responseParts.thinkingText(),
                "model loop sync"
        );
        if (!responseParts.thinkingText().isBlank()) {
            emitThinkingTranscript(request, outputEmitter, responseParts.thinkingText());
        }
        String finalText = responsePostProcessor.appendSourceAppendix(
                responseParts.visibleText(),
                sourceCollector,
                null
        );
        emitTextChunks(finalText, outputEmitter);
        return finalText;
    }

    @Override
    public AgentModelStep runSingleStep(String providerId,
                                        AgentExecutionRequest request,
                                        List<AgentGraphMessage> messages,
                                        String currentUserGraphMessageId,
                                        String prompt,
                                        List<ToolDescriptor> availableTools) {
        return runSingleStep(providerId, request, messages, currentUserGraphMessageId, prompt, availableTools, delta -> {
        });
    }

    @Override
    public AgentModelStep runSingleStep(String providerId,
                                        AgentExecutionRequest request,
                                        List<AgentGraphMessage> messages,
                                        String currentUserGraphMessageId,
                                        String prompt,
                                        List<ToolDescriptor> availableTools,
                                        AgentOutputEmitter outputEmitter) {
        ProviderClientResolver.ResolvedProviderClient resolvedProvider = resolveProvider(request.userId(), providerId);
        String resolvedProviderId = resolvedProvider.providerId();
        AgentExecutionRequest preparedRequest = imageInputOcrFallbackService.prepareForProvider(resolvedProviderId, request);
        try {
            return runSingleStepInternal(resolvedProvider, resolvedProviderId, preparedRequest, messages, currentUserGraphMessageId, prompt, availableTools, outputEmitter);
        } catch (RuntimeException exception) {
            if (!imageInputOcrFallbackService.shouldRetryWithOcr(exception, resolvedProviderId, request, preparedRequest)) {
                throw exception;
            }
            AgentExecutionRequest ocrRequest = imageInputOcrFallbackService.forceOcrFallback(resolvedProviderId, request);
            return runSingleStepInternal(resolvedProvider, resolvedProviderId, ocrRequest, messages, currentUserGraphMessageId, prompt, availableTools, outputEmitter);
        }
    }

    private AgentModelStep runSingleStepInternal(ProviderClientResolver.ResolvedProviderClient resolvedProvider,
                                                 String resolvedProviderId,
                                                 AgentExecutionRequest request,
                                                 List<AgentGraphMessage> messages,
                                                 String currentUserGraphMessageId,
                                                 String prompt,
                                                 List<ToolDescriptor> availableTools,
                                                 AgentOutputEmitter outputEmitter) {
        List<ToolCallback> toolCallbacks = createToolCallbacks(
                resolvedProviderId,
                request,
                availableTools,
                delta -> {
                },
                new AgentSourceCollector()
        );
        List<Message> promptMessages = messageMapper.prependSystem(
                prompt,
                messageMapper.toConversationMessages(resolvedProviderId, request, messages, currentUserGraphMessageId)
        );
        logFlow(() -> "runSingleStep provider=" + resolvedProviderId
                + " model=" + resolvedProvider.model()
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " messages=" + messages.size()
                + " toolsEnabled=" + !toolCallbacks.isEmpty());
        logSystemPrompt("single step", resolvedProviderId, resolvedProvider.model(), request, prompt);
        logConversationMessages("single step", resolvedProviderId, resolvedProvider.model(), request, promptMessages);
        ChatOptions options = buildOptions(
                resolvedProviderId,
                resolvedProvider.model(),
                null,
                false,
                "gemini".equalsIgnoreCase(resolvedProviderId)
        );
        AssistantResponseParts streamedResponse = streamAssistantResponse(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                () -> newRequestSpec(
                        resolvedProvider.chatModel(),
                        promptMessages,
                        options,
                        toolCallbacks
                ).stream().chatResponse().toIterable(),
                outputEmitter,
                false,
                "single step stream"
        );
        if (streamedResponse != null) {
            logToolCalls(
                    resolvedProviderId,
                    resolvedProvider.model(),
                    request,
                    streamedResponse.toolCalls(),
                    "single step stream"
            );
            logModelThinking(
                    resolvedProviderId,
                    resolvedProvider.model(),
                    request,
                    streamedResponse.thinkingText(),
                    "single step stream"
            );
            return new AgentModelStep(
                    streamedResponse.visibleText(),
                    streamedResponse.toolCalls(),
                    streamedResponse.assistantProperties()
            );
        }
        ChatResponse response = newRequestSpec(
                resolvedProvider.chatModel(),
                promptMessages,
                options,
                toolCallbacks
        ).call().chatResponse();
        AssistantResponseParts responseParts = splitAssistantResponse(resolvedProviderId, response, "single step");
        if (!responseParts.thinkingText().isBlank()) {
            emitThinkingTranscript(request, outputEmitter, responseParts.thinkingText());
        }
        logModelThinking(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                responseParts.thinkingText(),
                "single step"
        );
        logToolCalls(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                responseParts.toolCalls(),
                "single step"
        );
        return new AgentModelStep(
                responseParts.visibleText(),
                responseParts.toolCalls(),
                responseParts.assistantProperties()
        );
    }

    private ChatClient.ChatClientRequestSpec newRequestSpec(org.springframework.ai.chat.model.ChatModel chatModel,
                                                            List<Message> messages,
                                                            ChatOptions options,
                                                            List<ToolCallback> toolCallbacks) {
        ChatClient.ChatClientRequestSpec requestSpec = ChatClient.create(chatModel)
                .prompt()
                .messages(messages)
                .options(options);
        if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
            requestSpec = requestSpec.toolCallbacks(toolCallbacks);
        }
        return requestSpec;
    }
}
