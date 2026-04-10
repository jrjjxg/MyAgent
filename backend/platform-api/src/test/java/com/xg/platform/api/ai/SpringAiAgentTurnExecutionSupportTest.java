package com.xg.platform.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentGraphMessageType;
import com.xg.platform.agent.core.AgentModelStep;
import com.xg.platform.contracts.shared.event.RunEventType;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.xg.platform.api.ai.AgentTurnExecutionSupportTestSupport.createLegacySupport;
import static com.xg.platform.api.ai.AgentTurnExecutionSupportTestSupport.sampleRequest;
import static com.xg.platform.api.ai.AgentTurnExecutionSupportTestSupport.sampleTool;
import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAgentTurnExecutionSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void streamsModelLoopOutputWhenStreamingResponsesAreAvailable() {
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.StreamingFakeChatModel(List.of("hello ", "world"), "hello world")
        );
        List<String> emitted = new ArrayList<>();

        String result = support.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(), emitted::add);

        assertThat(result).isEqualTo("hello world");
        assertThat(emitted).containsExactly("hello ", "world");
    }

    @Test
    void fallsBackToSynchronousCallWhenStreamingFails() {
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.FallbackFakeChatModel("hello world")
        );
        List<String> emitted = new ArrayList<>();

        String result = support.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(), emitted::add);

        assertThat(result).isEqualTo("hello world");
        assertThat(String.join("", emitted)).isEqualTo("hello world");
    }

    @Test
    void streamsVisibleGeminiOutputWhenToolsAreEnabled() {
        AgentTurnExecutionSupportTestSupport.TrackingChatModel chatModel =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of("hello ", "world"), "hello world");
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(chatModel);
        List<String> emitted = new ArrayList<>();

        String result = support.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(sampleTool()), emitted::add);

        assertThat(result).isEqualTo("hello world");
        assertThat(chatModel.streamCalls()).isEqualTo(1);
        assertThat(chatModel.callCalls()).isZero();
        assertThat(emitted).containsExactly("hello ", "world");
    }

    @Test
    void enablesThoughtsForGeminiWhenToolsAreEnabled() {
        AgentTurnExecutionSupportTestSupport.TrackingChatModel chatModel =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of("hello "), "hello");
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(chatModel, "gemini-3-pro-preview");

        support.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(sampleTool()), delta -> {
        });

        assertThat(chatModel.lastGoogleOptions()).isNotNull();
        assertThat(chatModel.lastGoogleOptions().getIncludeThoughts()).isTrue();
    }

    @Test
    void keepsSingleStepVisiblePlanningOutOfProcessEventsWhenNoNativeThoughtsExist() {
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.ToolCallingChatModel("tool plan", "call-1", "web_search", "{\"query\":\"hi\"}")
        );
        AgentTurnExecutionSupportTestSupport.RecordingEmitter emitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();

        AgentModelStep step = support.runSingleStep(
                "gemini",
                sampleRequest(),
                List.of(new AgentGraphMessage(
                        "m-1",
                        AgentGraphMessageType.USER,
                        "hello",
                        List.of(),
                        "",
                        "",
                        Map.of()
                )),
                "m-1",
                "system prompt",
                List.of(sampleTool()),
                emitter
        );

        assertThat(step.content()).isEqualTo("tool plan");
        assertThat(step.toolCalls()).hasSize(1);
        assertThat(emitter.text()).isEmpty();
        assertThat(emitter.eventTypes()).isEmpty();
    }

    @Test
    void enablesThoughtsForGeminiSingleStepRequests() {
        AgentTurnExecutionSupportTestSupport.TrackingChatModel chatModel =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of("visible"), "visible");
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(chatModel, "gemini-3-pro-preview");

        support.runSingleStep(
                "gemini",
                sampleRequest(),
                List.of(new AgentGraphMessage(
                        "m-1",
                        AgentGraphMessageType.USER,
                        "hello",
                        List.of(),
                        "",
                        "",
                        Map.of()
                )),
                "m-1",
                "system prompt",
                List.of(sampleTool())
        );

        assertThat(chatModel.lastGoogleOptions()).isNotNull();
        assertThat(chatModel.lastGoogleOptions().getIncludeThoughts()).isTrue();
    }

    @Test
    void streamsGeminiThoughtPartsAsSeparateThinkingEvents() {
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.StreamingResponseChatModel(
                        List.of(
                                AgentTurnExecutionSupportTestSupport.response("Plan first.", Map.of("isThought", true)),
                                AgentTurnExecutionSupportTestSupport.response("Visible answer.", Map.of())
                        ),
                        AgentTurnExecutionSupportTestSupport.response("Visible answer.")
                ),
                "gemini-3-pro-preview"
        );
        AgentTurnExecutionSupportTestSupport.RecordingEmitter emitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();

        AgentModelStep step = support.runSingleStep(
                "gemini",
                sampleRequest(),
                List.of(new AgentGraphMessage(
                        "m-1",
                        AgentGraphMessageType.USER,
                        "hello",
                        List.of(),
                        "",
                        "",
                        Map.of()
                )),
                "m-1",
                "system prompt",
                List.of(sampleTool()),
                emitter
        );

        assertThat(step.content()).isEqualTo("Visible answer.");
        assertThat(emitter.eventTypes()).contains(
                RunEventType.MODEL_THINKING_STARTED,
                RunEventType.MODEL_THINKING_DELTA,
                RunEventType.MODEL_THINKING_COMPLETED,
                RunEventType.MODEL_THINKING
        );
        assertThat(emitter.eventPayloads()).anySatisfy(payload -> assertThat(payload.get("content")).isEqualTo("Plan first."));
        assertThat(emitter.eventPayloads()).noneSatisfy(payload -> assertThat(payload.get("content")).isEqualTo("Visible answer."));
    }

    @Test
    void fallsBackToVisibleAnswerWhileEmittingThoughtsForSynchronousGeminiSingleStep() {
        byte[] signature = new byte[]{1, 2, 3};
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(
                new AgentTurnExecutionSupportTestSupport.FallbackResponseChatModel(
                        AgentTurnExecutionSupportTestSupport.multiGenerationResponse(List.of(
                                AssistantMessage.builder()
                                        .content("Thought summary.")
                                        .properties(Map.of("isThought", true))
                                        .build(),
                                AssistantMessage.builder()
                                        .content("Final visible answer.")
                                        .properties(Map.of("thoughtSignatures", List.of(signature)))
                                        .build()
                        ))
                ),
                "gemini-3-pro-preview"
        );
        AgentTurnExecutionSupportTestSupport.RecordingEmitter emitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();

        AgentModelStep step = support.runSingleStep(
                "gemini",
                sampleRequest(),
                List.of(new AgentGraphMessage(
                        "m-1",
                        AgentGraphMessageType.USER,
                        "hello",
                        List.of(),
                        "",
                        "",
                        Map.of()
                )),
                "m-1",
                "system prompt",
                List.of(sampleTool()),
                emitter
        );

        assertThat(step.content()).isEqualTo("Final visible answer.");
        assertThat(step.assistantProperties()).containsKey("thoughtSignatures");
        assertThat(emitter.eventTypes()).contains(RunEventType.MODEL_THINKING);
        assertThat(emitter.eventPayloads()).anySatisfy(payload -> assertThat(payload.get("content")).isEqualTo("Thought summary."));
        assertThat(emitter.eventPayloads()).noneSatisfy(payload -> assertThat(payload.get("content")).isEqualTo("Final visible answer."));
    }

    @Test
    void sendsAttachedImagesAsMultimodalUserMessage() throws Exception {
        AgentTurnExecutionSupportTestSupport.TrackingChatModel chatModel =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of("done"), "done");
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(chatModel, "gemini-3-pro-preview");
        Path imagePath = tempDir.resolve("sample.png");
        Files.write(imagePath, new byte[]{1, 2, 3});

        support.runModelLoop("gemini", sampleRequest(List.of(new ThreadFileReference(
                "sample.png",
                "uploads/sample.png",
                imagePath.toString(),
                "image/png",
                3
        ))), "system prompt", List.of(), delta -> {
        });

        assertThat(chatModel.lastPrompt()).isNotNull();
        assertThat(chatModel.lastPrompt().getInstructions()).last().isInstanceOf(UserMessage.class);
        UserMessage userMessage = (UserMessage) chatModel.lastPrompt().getInstructions()
                .get(chatModel.lastPrompt().getInstructions().size() - 1);
        assertThat(userMessage.getText()).isEqualTo("Say hello");
        assertThat(userMessage.getMedia()).hasSize(1);
        assertThat(userMessage.getMedia().get(0).getName()).isEqualTo("sample.png");
    }

    @Test
    void ocrsAttachedImagesBeforeSendingToNonVisionProvider() throws Exception {
        AgentTurnExecutionSupportTestSupport.TrackingChatModel deepseekModel =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of(), "done");
        AgentTurnExecutionSupportTestSupport.TrackingChatModel geminiOcrModel =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of(), "Invoice 42");
        SpringAiAgentTurnExecutionSupport support = new SpringAiAgentTurnExecutionSupport(
                "deepseek",
                AgentTurnExecutionSupportTestSupport.resolver(Map.of(
                        "deepseek", new ProviderClientResolver.ResolvedProviderClient("deepseek", "deepseek-chat", deepseekModel),
                        "gemini", new ProviderClientResolver.ResolvedProviderClient("gemini", "gemini-3-pro-preview", geminiOcrModel)
                )),
                new AgentTurnExecutionSupportTestSupport.UnsupportedToolService(),
                false,
                false,
                false
        );
        Path imagePath = tempDir.resolve("invoice.png");
        Files.write(imagePath, new byte[]{1, 2, 3});

        support.runModelLoop("deepseek", AgentTurnExecutionSupportTestSupport.sampleRequest("deepseek", List.of(new ThreadFileReference(
                "invoice.png",
                "uploads/invoice.png",
                imagePath.toString(),
                "image/png",
                3
        ))), "system prompt", List.of(), delta -> {
        });

        UserMessage ocrPrompt = (UserMessage) geminiOcrModel.lastPrompt().getInstructions()
                .get(geminiOcrModel.lastPrompt().getInstructions().size() - 1);
        assertThat(ocrPrompt.getMedia()).hasSize(1);

        UserMessage textOnlyUserMessage = (UserMessage) deepseekModel.lastPrompt().getInstructions()
                .get(deepseekModel.lastPrompt().getInstructions().size() - 1);
        assertThat(textOnlyUserMessage.getMedia()).isEmpty();
        assertThat(textOnlyUserMessage.getText())
                .contains("OCR text from attached images:")
                .contains("Invoice 42");
    }

    @Test
    void retriesWithOcrWhenSelectedProviderRejectsImageMedia() throws Exception {
        AgentTurnExecutionSupportTestSupport.RetryOnMediaChatModel openAiTextModel =
                new AgentTurnExecutionSupportTestSupport.RetryOnMediaChatModel("done");
        AgentTurnExecutionSupportTestSupport.TrackingChatModel geminiOcrModel =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of(), "Order #7");
        SpringAiAgentTurnExecutionSupport support = new SpringAiAgentTurnExecutionSupport(
                "openai",
                AgentTurnExecutionSupportTestSupport.resolver(Map.of(
                        "openai", new ProviderClientResolver.ResolvedProviderClient("openai", "gpt-text-only", openAiTextModel),
                        "gemini", new ProviderClientResolver.ResolvedProviderClient("gemini", "gemini-3-pro-preview", geminiOcrModel)
                )),
                new AgentTurnExecutionSupportTestSupport.UnsupportedToolService(),
                false,
                false,
                false
        );
        Path imagePath = tempDir.resolve("receipt.png");
        Files.write(imagePath, new byte[]{1, 2, 3});

        String result = support.runModelLoop("openai", AgentTurnExecutionSupportTestSupport.sampleRequest("openai", List.of(new ThreadFileReference(
                "receipt.png",
                "uploads/receipt.png",
                imagePath.toString(),
                "image/png",
                3
        ))), "system prompt", List.of(), delta -> {
        });

        assertThat(result).isEqualTo("done");
        UserMessage retriedUserMessage = (UserMessage) openAiTextModel.lastPrompt().getInstructions()
                .get(openAiTextModel.lastPrompt().getInstructions().size() - 1);
        assertThat(retriedUserMessage.getMedia()).isEmpty();
        assertThat(retriedUserMessage.getText())
                .contains("OCR text from attached images:")
                .contains("Order #7");
    }

    @Test
    void keepsVisibleGeminiReasoningTextWhenProviderDoesNotExposeNativeThoughtParts() {
        String response = String.join("\n",
                "**Analyzing the User's Intent**",
                "",
                "I'm currently focused on fully grasping the user's need.",
                "",
                "Here is the answer the user should actually see."
        );
        AgentTurnExecutionSupportTestSupport.TrackingChatModel chatModel =
                new AgentTurnExecutionSupportTestSupport.TrackingChatModel(List.of(response), response);
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(chatModel, "gemini-3-pro-preview");
        AgentTurnExecutionSupportTestSupport.RecordingEmitter emitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();

        String result = support.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(sampleTool()), emitter);

        assertThat(result).isEqualTo(response);
        assertThat(String.join("", emitter.text())).isEqualTo(response);
        assertThat(emitter.eventTypes()).isEmpty();
    }

    @Test
    void streamsNativeGeminiThoughtPartsDuringModelLoop() {
        AgentTurnExecutionSupportTestSupport.StreamingResponseChatModel chatModel =
                new AgentTurnExecutionSupportTestSupport.StreamingResponseChatModel(
                        List.of(
                                AgentTurnExecutionSupportTestSupport.response("Plan first.", Map.of("isThought", true)),
                                AgentTurnExecutionSupportTestSupport.response("Visible answer for the user.", Map.of())
                        ),
                        AgentTurnExecutionSupportTestSupport.response("Visible answer for the user.")
                );
        SpringAiAgentTurnExecutionSupport support = createLegacySupport(chatModel, "gemini-3-pro-preview");
        AgentTurnExecutionSupportTestSupport.RecordingEmitter emitter =
                new AgentTurnExecutionSupportTestSupport.RecordingEmitter();

        String result = support.runModelLoop("gemini", sampleRequest(), "system prompt", List.of(sampleTool()), emitter);

        assertThat(result).isEqualTo("Visible answer for the user.");
        assertThat(String.join("", emitter.text())).isEqualTo("Visible answer for the user.");
        assertThat(emitter.eventTypes()).contains(RunEventType.MODEL_THINKING);
        assertThat(emitter.eventPayloads()).anySatisfy(payload -> assertThat(payload.get("content"))
                .contains("Plan first."));
    }

    @Test
    void prefersFetchedWebPagesInSourceAppendix() {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        AgentSourceCollector sourceCollector = new AgentSourceCollector();
        AgentResponsePostProcessor postProcessor = new AgentResponsePostProcessor();
        ObjectNode searchPayload = objectMapper.createObjectNode();
        searchPayload.putArray("results")
                .add(AgentTurnExecutionSupportTestSupport.resultNode(objectMapper, "Weather Search", "https://www.weather.com.cn/a"))
                .add(AgentTurnExecutionSupportTestSupport.resultNode(objectMapper, "Weather Search", "https://www.accuweather.com/b"));
        sourceCollector.capture("web_search", searchPayload);
        sourceCollector.capture("web_fetch", objectMapper.createObjectNode()
                .put("title", "Tianjin weather detail")
                .put("url", "https://www.weather.com.cn/weather/101030100.shtml"));

        String rendered = postProcessor.appendSourceAppendix("Weather answer", sourceCollector, null);

        assertThat(rendered).contains("Web Page");
        assertThat(rendered).contains("Tianjin weather detail");
        assertThat(rendered).doesNotContain("Search Result");
    }

    @Test
    void includesWeatherToolSourcesInSourceAppendix() {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        AgentSourceCollector sourceCollector = new AgentSourceCollector();
        AgentResponsePostProcessor postProcessor = new AgentResponsePostProcessor();
        ObjectNode weatherPayload = objectMapper.createObjectNode()
                .set("source", objectMapper.createObjectNode()
                        .put("title", "Weather forecast for Tianjin")
                        .put("domain", "wttr.in")
                        .put("url", "https://wttr.in/Tianjin?format=j1"));
        sourceCollector.capture("weather", weatherPayload);

        String rendered = postProcessor.appendSourceAppendix("Weather answer", sourceCollector, null);

        assertThat(rendered).contains("Weather Data");
        assertThat(rendered).contains("Weather forecast for Tianjin");
        assertThat(rendered).contains("wttr.in");
    }
}
