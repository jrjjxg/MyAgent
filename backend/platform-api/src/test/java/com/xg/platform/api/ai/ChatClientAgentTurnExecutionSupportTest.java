package com.xg.platform.api.ai;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentGraphMessageType;
import com.xg.platform.agent.core.AgentModelStep;
import com.xg.platform.contracts.shared.event.RunEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.xg.platform.api.ai.AgentTurnExecutionSupportTestSupport.createChatClientSupport;
import static com.xg.platform.api.ai.AgentTurnExecutionSupportTestSupport.createLegacySupport;
import static com.xg.platform.api.ai.AgentTurnExecutionSupportTestSupport.sampleRequest;
import static com.xg.platform.api.ai.AgentTurnExecutionSupportTestSupport.sampleTool;
import static org.assertj.core.api.Assertions.assertThat;

class ChatClientAgentTurnExecutionSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void matchesLegacyForStreamingModelLoopOutput() {
        SpringAiAgentTurnExecutionSupport legacy = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.StreamingFakeChatModel(List.of("hello ", "world"), "hello world")
        );
        ChatClientAgentTurnExecutionSupport chatClient = createChatClientSupport(
                new AgentTurnExecutionSupportTestSupport.StreamingFakeChatModel(List.of("hello ", "world"), "hello world")
        );
        List<String> legacyEmitted = new ArrayList<>();
        List<String> chatClientEmitted = new ArrayList<>();

        String legacyResult = legacy.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(), legacyEmitted::add);
        String chatClientResult = chatClient.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(), chatClientEmitted::add);

        assertThat(chatClientResult).isEqualTo(legacyResult);
        assertThat(chatClientEmitted).containsExactlyElementsOf(legacyEmitted);
    }

    @Test
    void matchesLegacyWhenStreamingFallsBackToSynchronousCall() {
        SpringAiAgentTurnExecutionSupport legacy = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.FallbackFakeChatModel("hello world")
        );
        ChatClientAgentTurnExecutionSupport chatClient = createChatClientSupport(
                new AgentTurnExecutionSupportTestSupport.FallbackFakeChatModel("hello world")
        );
        List<String> legacyEmitted = new ArrayList<>();
        List<String> chatClientEmitted = new ArrayList<>();

        String legacyResult = legacy.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(), legacyEmitted::add);
        String chatClientResult = chatClient.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(), chatClientEmitted::add);

        assertThat(chatClientResult).isEqualTo(legacyResult);
        assertThat(chatClientEmitted).containsExactlyElementsOf(legacyEmitted);
    }

    @Test
    void matchesLegacyForGeminiToolStreamingOutput() {
        SpringAiAgentTurnExecutionSupport legacy = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of("hello ", "world"), "hello world"),
                "gemini-3-pro-preview"
        );
        ChatClientAgentTurnExecutionSupport chatClient = createChatClientSupport(
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of("hello ", "world"), "hello world"),
                "gemini-3-pro-preview"
        );
        AgentTurnExecutionSupportTestSupport.RecordingEmitter legacyEmitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();
        AgentTurnExecutionSupportTestSupport.RecordingEmitter chatClientEmitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();

        String legacyResult = legacy.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(sampleTool()), legacyEmitter);
        String chatClientResult = chatClient.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(sampleTool()), chatClientEmitter);

        assertThat(chatClientResult).isEqualTo(legacyResult);
        assertThat(chatClientEmitter.text()).containsExactlyElementsOf(legacyEmitter.text());
        assertThat(chatClientEmitter.eventTypes()).containsExactlyElementsOf(legacyEmitter.eventTypes());
    }

    @Test
    void matchesLegacyForTextTurns() {
        SpringAiAgentTurnExecutionSupport legacy = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.StreamingFakeChatModel(List.of("unused"), "plain result")
        );
        ChatClientAgentTurnExecutionSupport chatClient = createChatClientSupport(
                new AgentTurnExecutionSupportTestSupport.StreamingFakeChatModel(List.of("unused"), "plain result")
        );

        String legacyResult = legacy.runTextTurn("gemini", null, "system prompt", "hello");
        String chatClientResult = chatClient.runTextTurn("gemini", null, "system prompt", "hello");

        assertThat(chatClientResult).isEqualTo(legacyResult);
    }

    @Test
    void matchesLegacyForSingleStepToolCallExtraction() {
        SpringAiAgentTurnExecutionSupport legacy = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.ToolCallingChatModel("tool plan", "call-1", "web_search", "{\"query\":\"hi\"}")
        );
        ChatClientAgentTurnExecutionSupport chatClient = createChatClientSupport(
                new AgentTurnExecutionSupportTestSupport.ToolCallingChatModel("tool plan", "call-1", "web_search", "{\"query\":\"hi\"}")
        );
        List<AgentGraphMessage> messages = List.of(new AgentGraphMessage(
                "m-1",
                AgentGraphMessageType.USER,
                "hello",
                List.of(),
                "",
                "",
                java.util.Map.of()
        ));

        AgentModelStep legacyResult = legacy.runSingleStep(
                "gemini",
                sampleRequest(),
                messages,
                "m-1",
                "system prompt",
                List.of(sampleTool())
        );
        AgentModelStep chatClientResult = chatClient.runSingleStep(
                "gemini",
                sampleRequest(),
                messages,
                "m-1",
                "system prompt",
                List.of(sampleTool())
        );

        assertThat(chatClientResult.content()).isEqualTo(legacyResult.content());
        assertThat(chatClientResult.toolCalls()).hasSize(1);
        assertThat(chatClientResult.toolCalls().get(0).id()).isEqualTo(legacyResult.toolCalls().get(0).id());
        assertThat(chatClientResult.toolCalls().get(0).name()).isEqualTo(legacyResult.toolCalls().get(0).name());
        assertThat(chatClientResult.toolCalls().get(0).arguments())
                .isEqualTo(JsonNodeFactory.instance.objectNode().put("query", "hi"));
    }

    @Test
    void matchesLegacyForSingleStepNativeThinkingStream() {
        SpringAiAgentTurnExecutionSupport legacy = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.ToolCallingChatModel("tool plan", "call-1", "web_search", "{\"query\":\"hi\"}")
        );
        ChatClientAgentTurnExecutionSupport chatClient = createChatClientSupport(
                new AgentTurnExecutionSupportTestSupport.ToolCallingChatModel("tool plan", "call-1", "web_search", "{\"query\":\"hi\"}")
        );
        List<AgentGraphMessage> messages = List.of(new AgentGraphMessage(
                "m-1",
                AgentGraphMessageType.USER,
                "hello",
                List.of(),
                "",
                "",
                java.util.Map.of()
        ));
        AgentTurnExecutionSupportTestSupport.RecordingEmitter legacyEmitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();
        AgentTurnExecutionSupportTestSupport.RecordingEmitter chatClientEmitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();

        AgentModelStep legacyResult = legacy.runSingleStep(
                "gemini",
                sampleRequest(),
                messages,
                "m-1",
                "system prompt",
                List.of(sampleTool()),
                legacyEmitter
        );
        AgentModelStep chatClientResult = chatClient.runSingleStep(
                "gemini",
                sampleRequest(),
                messages,
                "m-1",
                "system prompt",
                List.of(sampleTool()),
                chatClientEmitter
        );

        assertThat(chatClientResult.content()).isEqualTo(legacyResult.content());
        assertThat(chatClientResult.toolCalls()).hasSize(1);
        assertThat(legacyEmitter.text()).isEmpty();
        assertThat(chatClientEmitter.text()).isEmpty();
        assertThat(chatClientEmitter.text()).containsExactlyElementsOf(legacyEmitter.text());
        assertThat(chatClientEmitter.eventTypes()).containsExactlyElementsOf(legacyEmitter.eventTypes());
        assertThat(chatClientEmitter.eventTypes()).isEmpty();
        assertThat(chatClientEmitter.eventPayloads()).containsExactlyElementsOf(legacyEmitter.eventPayloads());
    }

    @Test
    void matchesLegacyForSingleStepThinkingEvents() {
        SpringAiAgentTurnExecutionSupport legacy = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.StreamingResponseChatModel(
                        List.of(
                                AgentTurnExecutionSupportTestSupport.response("Thought summary.", Map.of("isThought", true)),
                                AgentTurnExecutionSupportTestSupport.response("Visible answer.", Map.of())
                        ),
                        AgentTurnExecutionSupportTestSupport.response("Visible answer.")
                ),
                "gemini-3-pro-preview"
        );
        ChatClientAgentTurnExecutionSupport chatClient = createChatClientSupport(
                new AgentTurnExecutionSupportTestSupport.StreamingResponseChatModel(
                        List.of(
                                AgentTurnExecutionSupportTestSupport.response("Thought summary.", Map.of("isThought", true)),
                                AgentTurnExecutionSupportTestSupport.response("Visible answer.", Map.of())
                        ),
                        AgentTurnExecutionSupportTestSupport.response("Visible answer.")
                ),
                "gemini-3-pro-preview"
        );
        List<AgentGraphMessage> messages = List.of(new AgentGraphMessage(
                "m-1",
                AgentGraphMessageType.USER,
                "hello",
                List.of(),
                "",
                "",
                java.util.Map.of()
        ));
        AgentTurnExecutionSupportTestSupport.RecordingEmitter legacyEmitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();
        AgentTurnExecutionSupportTestSupport.RecordingEmitter chatClientEmitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();

        AgentModelStep legacyResult = legacy.runSingleStep(
                "gemini",
                sampleRequest(),
                messages,
                "m-1",
                "system prompt",
                List.of(sampleTool()),
                legacyEmitter
        );
        AgentModelStep chatClientResult = chatClient.runSingleStep(
                "gemini",
                sampleRequest(),
                messages,
                "m-1",
                "system prompt",
                List.of(sampleTool()),
                chatClientEmitter
        );

        assertThat(chatClientResult.content()).isEqualTo(legacyResult.content());
        assertThat(chatClientEmitter.eventTypes()).containsExactlyElementsOf(legacyEmitter.eventTypes());
        assertThat(chatClientEmitter.eventPayloads()).containsExactlyElementsOf(legacyEmitter.eventPayloads());
        assertThat(chatClientEmitter.eventTypes()).contains(RunEventType.MODEL_THINKING);
    }

    @Test
    void matchesLegacyForImageOcrFallback() throws Exception {
        AgentTurnExecutionSupportTestSupport.RetryOnMediaChatModel legacyOpenAi =
                new AgentTurnExecutionSupportTestSupport.RetryOnMediaChatModel("done");
        AgentTurnExecutionSupportTestSupport.TrackingChatModel legacyGeminiOcr =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of(), "Order #9");
        SpringAiAgentTurnExecutionSupport legacy = new SpringAiAgentTurnExecutionSupport(
                "openai",
                AgentTurnExecutionSupportTestSupport.resolver(Map.of(
                        "openai", new ProviderClientResolver.ResolvedProviderClient("openai", "gpt-text-only", legacyOpenAi),
                        "gemini", new ProviderClientResolver.ResolvedProviderClient("gemini", "gemini-3-pro-preview", legacyGeminiOcr)
                )),
                new AgentTurnExecutionSupportTestSupport.UnsupportedToolService(),
                false,
                false,
                false
        );
        AgentTurnExecutionSupportTestSupport.RetryOnMediaChatModel chatClientOpenAi =
                new AgentTurnExecutionSupportTestSupport.RetryOnMediaChatModel("done");
        AgentTurnExecutionSupportTestSupport.TrackingChatModel chatClientGeminiOcr =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of(), "Order #9");
        ChatClientAgentTurnExecutionSupport chatClient = new ChatClientAgentTurnExecutionSupport(
                "openai",
                AgentTurnExecutionSupportTestSupport.resolver(Map.of(
                        "openai", new ProviderClientResolver.ResolvedProviderClient("openai", "gpt-text-only", chatClientOpenAi),
                        "gemini", new ProviderClientResolver.ResolvedProviderClient("gemini", "gemini-3-pro-preview", chatClientGeminiOcr)
                )),
                new AgentTurnExecutionSupportTestSupport.UnsupportedToolService(),
                false,
                false,
                false
        );
        Path imagePath = tempDir.resolve("receipt.png");
        Files.write(imagePath, new byte[]{1, 2, 3});

        String legacyResult = legacy.runModelLoop("openai", AgentTurnExecutionSupportTestSupport.sampleRequest("openai", List.of(new com.xg.platform.contracts.conversation.ThreadFileReference(
                "receipt.png",
                "uploads/receipt.png",
                imagePath.toString(),
                "image/png",
                3
        ))), "system prompt", List.of(), delta -> {
        });
        String chatClientResult = chatClient.runModelLoop("openai", AgentTurnExecutionSupportTestSupport.sampleRequest("openai", List.of(new com.xg.platform.contracts.conversation.ThreadFileReference(
                "receipt.png",
                "uploads/receipt.png",
                imagePath.toString(),
                "image/png",
                3
        ))), "system prompt", List.of(), delta -> {
        });

        assertThat(chatClientResult).isEqualTo(legacyResult);
        var legacyUserMessage = (org.springframework.ai.chat.messages.UserMessage) legacyOpenAi.lastPrompt().getInstructions()
                .get(legacyOpenAi.lastPrompt().getInstructions().size() - 1);
        var chatClientUserMessage = (org.springframework.ai.chat.messages.UserMessage) chatClientOpenAi.lastPrompt().getInstructions()
                .get(chatClientOpenAi.lastPrompt().getInstructions().size() - 1);
        assertThat(chatClientUserMessage.getMedia()).isEmpty();
        assertThat(chatClientUserMessage.getText()).isEqualTo(legacyUserMessage.getText());
    }
}
