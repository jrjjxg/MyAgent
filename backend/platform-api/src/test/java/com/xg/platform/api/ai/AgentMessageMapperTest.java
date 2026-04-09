package com.xg.platform.api.ai;

import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentGraphMessageType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMessageMapperTest {

    @Test
    void mapsToolResponsesWithToolCallIdAsIdAndToolNameAsName() {
        AgentMessageMapper mapper = new AgentMessageMapper();
        AgentGraphMessage toolMessage = new AgentGraphMessage(
                "msg-1",
                AgentGraphMessageType.TOOL,
                "{\"status\":\"ok\"}",
                List.of(),
                "web_search",
                "call-123",
                java.util.Map.of()
        );

        List<Message> messages = mapper.toConversationMessages(
                "gemini",
                AgentTurnExecutionSupportTestSupport.sampleRequest(),
                List.of(toolMessage),
                null
        );

        assertThat(messages).singleElement().isInstanceOf(ToolResponseMessage.class);
        ToolResponseMessage responseMessage = (ToolResponseMessage) messages.get(0);
        assertThat(responseMessage.getResponses()).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo("call-123");
            assertThat(response.name()).isEqualTo("web_search");
            assertThat(response.responseData()).isEqualTo("{\"status\":\"ok\"}");
        });
    }

    @Test
    void preservesAssistantMessagePropertiesForToolLoopReplay() {
        AgentMessageMapper mapper = new AgentMessageMapper();
        byte[] signature = new byte[]{1, 2, 3};
        AgentGraphMessage assistantMessage = AgentGraphMessage.assistant(
                "msg-2",
                "",
                List.of(),
                Map.of("thoughtSignatures", List.of(signature))
        );

        List<Message> messages = mapper.toConversationMessages(
                "gemini",
                AgentTurnExecutionSupportTestSupport.sampleRequest(),
                List.of(assistantMessage),
                null
        );

        assertThat(messages).singleElement().isInstanceOf(AssistantMessage.class);
        AssistantMessage mappedAssistant = (AssistantMessage) messages.get(0);
        assertThat(mappedAssistant.getMetadata()).containsKey("thoughtSignatures");
        List<byte[]> thoughtSignatures = (List<byte[]>) mappedAssistant.getMetadata().get("thoughtSignatures");
        assertThat(thoughtSignatures).hasSize(1);
        assertThat(thoughtSignatures.get(0)).containsExactly(signature);
    }

    @Test
    @SuppressWarnings("unchecked")
    void decodesBase64ThoughtSignaturesForGeminiReplay() {
        AgentMessageMapper mapper = new AgentMessageMapper();
        byte[] signature = new byte[]{4, 5, 6};
        AgentGraphMessage assistantMessage = AgentGraphMessage.assistant(
                "msg-legacy",
                "",
                List.of(),
                Map.of("thoughtSignatures", List.of(Base64.getEncoder().encodeToString(signature)))
        );

        List<Message> messages = mapper.toConversationMessages(
                "gemini",
                AgentTurnExecutionSupportTestSupport.sampleRequest(),
                List.of(assistantMessage),
                null
        );

        assertThat(messages).singleElement().isInstanceOf(AssistantMessage.class);
        AssistantMessage mappedAssistant = (AssistantMessage) messages.get(0);
        List<byte[]> thoughtSignatures = (List<byte[]>) mappedAssistant.getMetadata().get("thoughtSignatures");
        assertThat(thoughtSignatures).hasSize(1);
        assertThat(thoughtSignatures.get(0)).containsExactly(signature);
    }

    @Test
    void prefersCompressedModelContextForToolReplayWhenPresent() {
        AgentMessageMapper mapper = new AgentMessageMapper();
        AgentGraphMessage toolMessage = AgentGraphMessage.tool(
                "msg-3",
                "read_document",
                "call-789",
                "{\"content\":\"full raw content\"}",
                Map.of("modelContext", "{\"summary\":\"compressed\"}")
        );

        List<Message> messages = mapper.toConversationMessages(
                "gemini",
                AgentTurnExecutionSupportTestSupport.sampleRequest(),
                List.of(toolMessage),
                null
        );

        assertThat(messages).singleElement().isInstanceOf(ToolResponseMessage.class);
        ToolResponseMessage responseMessage = (ToolResponseMessage) messages.get(0);
        assertThat(responseMessage.getResponses()).singleElement().satisfies(response ->
                assertThat(response.responseData()).isEqualTo("{\"summary\":\"compressed\"}")
        );
    }
}
