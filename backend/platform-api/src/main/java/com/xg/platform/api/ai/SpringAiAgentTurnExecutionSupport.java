package com.xg.platform.api.ai;

import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentModelStep;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.tools.ToolDescriptor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SpringAiAgentTurnExecutionSupport extends AbstractSpringAiAgentTurnExecutionSupport {

    private static final Logger logger = Logger.getLogger(SpringAiAgentTurnExecutionSupport.class.getName());

    public SpringAiAgentTurnExecutionSupport(String defaultProviderId,
                                             ProviderClientResolver providerClientResolver,
                                             AgentToolService agentToolService,
                                             boolean logPrompts,
                                             boolean logAgentFlow,
                                             boolean logModelResponses) {
        super(defaultProviderId, providerClientResolver, agentToolService, logPrompts, logAgentFlow, logModelResponses);
    }

    SpringAiAgentTurnExecutionSupport(String defaultProviderId,
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
        List<Message> promptMessages = List.of(
                new org.springframework.ai.chat.messages.SystemMessage(prompt),
                new org.springframework.ai.chat.messages.UserMessage(userMessage)
        );
        logSystemPrompt("text turn", resolvedProviderId, resolvedProvider.model(), prompt);
        logConversationMessages("text turn", resolvedProviderId, resolvedProvider.model(), null, promptMessages);
        ChatResponse response = resolvedProvider.chatModel().call(new Prompt(
                promptMessages,
                buildOptions(resolvedProviderId, resolvedProvider.model(), modelOverride, false)
        ));
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
        ChatModel chatModel = resolvedProvider.chatModel();
        List<Message> conversationMessages = new ArrayList<>(messageMapper.toConversationMessages(request.recentMessages()));
        conversationMessages.add(messageMapper.toCurrentUserMessage(resolvedProviderId, request));
        AgentSourceCollector sourceCollector = new AgentSourceCollector();
        List<ToolCallback> toolCallbacks = createToolCallbacks(
                resolvedProviderId,
                request,
                availableTools,
                outputEmitter,
                sourceCollector
        );
        List<Message> promptMessages = messageMapper.prependSystem(prompt, conversationMessages);
        Prompt promptRequest = new Prompt(
                promptMessages,
                buildOptions(
                        resolvedProviderId,
                        resolvedProvider.model(),
                        null,
                        toolCallbacks,
                        !toolCallbacks.isEmpty()
                )
        );
        boolean suppressVisibleStreaming = shouldSuppressVisibleStreaming(
                resolvedProviderId,
                resolvedProvider.model(),
                toolCallbacks
        );
        logFlow(() -> "runModelLoop provider=" + resolvedProviderId
                + " model=" + resolvedProvider.model()
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " messages=" + conversationMessages.size()
                + " toolsEnabled=" + !toolCallbacks.isEmpty());
        logSystemPrompt("model loop", resolvedProviderId, resolvedProvider.model(), request, prompt);
        logConversationMessages("model loop", resolvedProviderId, resolvedProvider.model(), request, promptMessages);
        String streamedResponse = streamTextResponse(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                () -> chatModel.stream(promptRequest).toIterable(),
                outputEmitter,
                !suppressVisibleStreaming
        );
        if (streamedResponse != null) {
            AgentResponsePostProcessor.SanitizedResponse sanitizedResponse =
                    responsePostProcessor.sanitizeVisibleResponse(resolvedProviderId, streamedResponse);
            logModelThinking(
                    resolvedProviderId,
                    resolvedProvider.model(),
                    request,
                    sanitizedResponse.hiddenReasoning(),
                    "model loop stream"
            );
            responsePostProcessor.emitModelThinking(outputEmitter, sanitizedResponse.hiddenReasoning());
            String finalText = responsePostProcessor.appendSourceAppendix(
                    sanitizedResponse.visibleText(),
                    sourceCollector,
                    suppressVisibleStreaming ? null : outputEmitter
            );
            if (suppressVisibleStreaming) {
                emitTextChunks(finalText, outputEmitter);
            }
            return finalText;
        }
        long callStartedAt = System.nanoTime();
        logFlow(() -> "runModelLoop sync call start provider=" + resolvedProviderId
                + " model=" + resolvedProvider.model()
                + " thread=" + request.threadId()
                + " run=" + request.runId());
        ChatResponse response = chatModel.call(promptRequest);
        long callElapsedMs = elapsedMillis(callStartedAt);
        logFlow(() -> "runModelLoop sync call finished provider=" + resolvedProviderId
                + " model=" + resolvedProvider.model()
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " elapsedMs=" + callElapsedMs);
        AgentResponsePostProcessor.SanitizedResponse sanitizedResponse =
                responsePostProcessor.sanitizeVisibleResponse(
                        resolvedProviderId,
                        requireText(resolvedProviderId, response, "model loop")
                );
        logModelThinking(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                sanitizedResponse.hiddenReasoning(),
                "model loop sync"
        );
        responsePostProcessor.emitModelThinking(outputEmitter, sanitizedResponse.hiddenReasoning());
        String finalText = responsePostProcessor.appendSourceAppendix(
                sanitizedResponse.visibleText(),
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
        Prompt promptRequest = new Prompt(
                messageMapper.prependSystem(
                        prompt,
                        messageMapper.toConversationMessages(
                                resolvedProviderId,
                                request,
                                messages,
                                currentUserGraphMessageId
                        )
                ),
                buildOptions(
                        resolvedProviderId,
                        resolvedProvider.model(),
                        null,
                        toolCallbacks,
                        false,
                        "gemini".equalsIgnoreCase(resolvedProviderId)
                )
        );
        logFlow(() -> "runSingleStep provider=" + resolvedProviderId
                + " model=" + resolvedProvider.model()
                + " thread=" + request.threadId()
                + " run=" + request.runId()
                + " messages=" + messages.size()
                + " toolsEnabled=" + !toolCallbacks.isEmpty());
        logSystemPrompt("single step", resolvedProviderId, resolvedProvider.model(), request, prompt);
        logConversationMessages(
                "single step",
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                promptRequest.getInstructions()
        );
        AgentModelStep streamedStep = streamSingleStep(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                () -> resolvedProvider.chatModel().stream(promptRequest).toIterable(),
                outputEmitter
        );
        if (streamedStep != null) {
            logToolCalls(
                    resolvedProviderId,
                    resolvedProvider.model(),
                    request,
                    streamedStep.toolCalls(),
                    "single step stream"
            );
            return streamedStep;
        }
        ChatResponse response = resolvedProvider.chatModel().call(promptRequest);
        AssistantResponseParts responseParts = splitAssistantResponse(resolvedProviderId, response, "single step");
        AgentResponsePostProcessor.SanitizedResponse sanitizedResponse =
                responsePostProcessor.sanitizeVisibleResponse(resolvedProviderId, responseParts.visibleText());
        String thinkingContent = combineThinkingContent(responseParts.thinkingText(), sanitizedResponse.hiddenReasoning());
        if (!thinkingContent.isBlank()) {
            emitThinkingTranscript(request, outputEmitter, thinkingContent);
        }
        if (!sanitizedResponse.visibleText().isBlank()) {
            emitAgentStepTranscript(request, outputEmitter, sanitizedResponse.visibleText());
        }
        logModelThinking(
                resolvedProviderId,
                resolvedProvider.model(),
                request,
                thinkingContent,
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
                sanitizedResponse.visibleText(),
                responseParts.toolCalls(),
                responseParts.assistantProperties()
        );
    }
}
