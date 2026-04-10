package com.xg.platform.shared.runtime.graph;

import com.xg.platform.contracts.shared.event.RunEvent;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import com.xg.platform.conversation.runtime.InteractionState;
import com.xg.platform.research.runtime.ResearchTaskState;

public class PlatformGraphRunner {

    private final CompiledGraph<InteractionState> interactionGraph;
    private final CompiledGraph<ResearchTaskState> researchGraph;
    private final RunEventConsumerRegistry runEventConsumerRegistry;

    public PlatformGraphRunner(CompiledGraph<InteractionState> interactionGraph,
                               CompiledGraph<ResearchTaskState> researchGraph,
                               RunEventConsumerRegistry runEventConsumerRegistry) {
        this.interactionGraph = interactionGraph;
        this.researchGraph = researchGraph;
        this.runEventConsumerRegistry = runEventConsumerRegistry;
    }

    public Optional<InteractionState> invokeInteraction(Map<String, Object> inputs,
                                                        String threadId,
                                                        Consumer<RunEvent> runEventConsumer) {
        String runContextKey = "interaction-event:" + threadId + ":" + UUID.randomUUID();
        return invokeWithRunEventConsumer(
                interactionGraph,
                withInput(inputs, InteractionState.RUN_CONTEXT_KEY, runContextKey),
                "interaction:" + threadId + ":" + UUID.randomUUID(),
                runContextKey,
                runEventConsumer,
                "Failed to execute interaction graph"
        );
    }

    public Optional<ResearchTaskState> invokeResearch(Map<String, Object> inputs,
                                                      String taskId) {
        return invokeCompiledGraph(
                researchGraph,
                inputs,
                "task:" + taskId,
                "Failed to execute research graph"
        );
    }

    private <T extends AgentState> Optional<T> invokeWithRunEventConsumer(CompiledGraph<T> graph,
                                                                          Map<String, Object> inputs,
                                                                          String graphThreadId,
                                                                          String runContextKey,
                                                                          Consumer<RunEvent> runEventConsumer,
                                                                          String errorMessage) {
        runEventConsumerRegistry.register(runContextKey, runEventConsumer);
        try {
            return invokeCompiledGraph(graph, inputs, graphThreadId, errorMessage);
        } finally {
            runEventConsumerRegistry.remove(runContextKey);
        }
    }

    private <T extends AgentState> Optional<T> invokeCompiledGraph(CompiledGraph<T> graph,
                                                                   Map<String, Object> inputs,
                                                                   String graphThreadId,
                                                                   String errorMessage) {
        try {
            return graph.invoke(inputs, RunnableConfig.builder().threadId(graphThreadId).build());
        } catch (RuntimeException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private static Map<String, Object> withInput(Map<String, Object> inputs,
                                                 String key,
                                                 Object value) {
        Map<String, Object> mergedInputs = new HashMap<>(inputs);
        mergedInputs.put(key, value);
        return mergedInputs;
    }
}
