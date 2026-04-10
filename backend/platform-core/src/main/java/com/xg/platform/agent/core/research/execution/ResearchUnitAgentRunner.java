package com.xg.platform.agent.core.research.execution;

import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.tooling.domain.ToolDescriptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ResearchUnitAgentRunner {

    private final AgentTurnExecutionSupport agentTurnExecutionSupport;

    public ResearchUnitAgentRunner(AgentTurnExecutionSupport agentTurnExecutionSupport) {
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
    }

    public String run(String providerId,
                      AgentExecutionRequest request,
                      String prompt,
                      List<ToolDescriptor> availableTools,
                      AgentOutputEmitter outputEmitter) {
        long timeoutMs = request.toolUseLimits() == null ? 120_000L : request.toolUseLimits().timeoutMs();
        try {
            return CompletableFuture.supplyAsync(() -> agentTurnExecutionSupport.runModelLoop(
                            providerId,
                            request,
                            prompt,
                            availableTools,
                            outputEmitter
                    ))
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof TimeoutException) {
                return """
                        {
                          "notes": "The research unit timed out before it could finish. Provide a best-effort answer and mark evidence limits clearly.",
                          "localConclusion": "The research unit exceeded its time budget before reaching a fully grounded conclusion.",
                          "sources": []
                        }
                        """;
            }
            throw exception;
        }
    }
}
