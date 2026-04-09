package com.xg.platform.api.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.api.ai.ChatClientAgentTurnExecutionSupport;
import com.xg.platform.api.ai.ProviderClientResolver;
import com.xg.platform.api.ai.SpringAiAgentTurnExecutionSupport;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolExecutionRequest;
import com.xg.platform.tools.ToolExecutionResult;
import com.xg.platform.tools.ToolGroup;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigTest {

    @Test
    void defaultsToLegacyExecutionSupport() {
        PlatformProperties properties = new PlatformProperties();

        AgentTurnExecutionSupport support = new AiConfig().agentTurnExecutionSupport(
                new StubToolService(),
                resolver(),
                properties
        );

        assertThat(support).isInstanceOf(SpringAiAgentTurnExecutionSupport.class);
    }

    @Test
    void usesChatClientExecutionSupportWhenConfigured() {
        PlatformProperties properties = new PlatformProperties();
        properties.getAi().getExecution().setImpl("chatclient");

        AgentTurnExecutionSupport support = new AiConfig().agentTurnExecutionSupport(
                new StubToolService(),
                resolver(),
                properties
        );

        assertThat(support).isInstanceOf(ChatClientAgentTurnExecutionSupport.class);
    }

    private ProviderClientResolver resolver() {
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of());
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.empty();
            }
        };
        return (userId, requestedProviderId) ->
                new ProviderClientResolver.ResolvedProviderClient("gemini", "test-model", chatModel);
    }

    private static final class StubToolService implements AgentToolService {

        @Override
        public List<ToolDescriptor> listAvailableTools(String userId) {
            return List.of(new ToolDescriptor(
                    "web_search",
                    "Search the web",
                    JsonMapper.builder().findAndAddModules().build().createObjectNode(),
                    ToolGroup.SEARCH,
                    "builtin"
            ));
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
