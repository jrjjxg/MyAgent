package com.xg.platform.graph;

import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class GraphRuntimeFactory {

    private final CompiledGraph<InteractionState> interactionGraph;
    private final CompiledGraph<ResearchTaskState> researchGraph;
    private final RunEventConsumerRegistry runEventConsumerRegistry;

    public GraphRuntimeFactory(CompiledGraph<InteractionState> interactionGraph,
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

    public static CompiledGraph<InteractionState> compileInteractionGraph(CheckpointConfiguration checkpointConfiguration,
                                                                          InteractionGraphNodes nodes) {
        try {
            return new StateGraph<>(InteractionState.SCHEMA, checkpointConfiguration.interactionStateSerializer())
                    .addNode("loadShortTermMemory", node_async(nodes::loadShortTermMemory))
                    .addNode("loadLongTermMemory", node_async(nodes::loadLongTermMemory))
                    .addNode("loadDraftContext", node_async(nodes::loadDraftContext))
                    .addNode("routeInteraction", node_async(nodes::routeInteraction))
                    .addNode("prepareAgentStep", node_async(nodes::prepareAgentStep))
                    .addNode("agent", node_async(nodes::agent))
                    .addNode("executeTools", node_async(nodes::executeTools))
                    .addNode("runScopingFrame", node_async(nodes::runScopingFrame))
                    .addNode("persistDraft", node_async(nodes::persistDraft))
                    .addNode("persistAssistantMessage", node_async(nodes::persistAssistantMessage))
                    .addNode("persistTurnArtifacts", node_async(nodes::persistTurnArtifacts))
                    .addNode("publishTurnEvents", node_async(nodes::publishTurnEvents))
                    .addEdge(START, "loadShortTermMemory")
                    .addEdge(START, "loadLongTermMemory")
                    .addEdge(START, "loadDraftContext")
                    .addEdge("loadShortTermMemory", "routeInteraction")
                    .addEdge("loadLongTermMemory", "routeInteraction")
                    .addEdge("loadDraftContext", "routeInteraction")
                    .addConditionalEdges("routeInteraction",
                            edge_async(state -> state.routeKind()
                                    .map(ChatRouteKind.class::cast)
                                    .filter(kind -> kind == ChatRouteKind.RESEARCH_DRAFT)
                                    .map(ignored -> "scoping")
                                    .orElse("agent")),
                            Map.of(
                                    "agent", "prepareAgentStep",
                                    "scoping", "runScopingFrame"
                            ))
                    .addEdge("prepareAgentStep", "agent")
                    .addConditionalEdges("agent",
                            edge_async(state -> hasPendingToolCalls(state) ? "tools" : "persist"),
                            Map.of(
                                    "tools", "executeTools",
                                    "persist", "persistTurnArtifacts"
                            ))
                    .addEdge("executeTools", "agent")
                    .addEdge("runScopingFrame", "persistDraft")
                    .addEdge("persistDraft", "persistAssistantMessage")
                    .addEdge("persistAssistantMessage", "publishTurnEvents")
                    .addEdge("persistTurnArtifacts", "publishTurnEvents")
                    .addEdge("publishTurnEvents", END)
                    .compile(checkpointConfiguration.interactionCompileConfig());
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile interaction graph", exception);
        }
    }

    public static CompiledGraph<ResearchTaskState> compileResearchGraph(CheckpointConfiguration checkpointConfiguration,
                                                                        ResearchGraphNodes nodes) {
        try {
            return new StateGraph<>(ResearchTaskState.SCHEMA, checkpointConfiguration.researchStateSerializer())
                    .addNode("hydrateTask", node_async(nodes::hydrateTask))
                    .addNode("normalizePlan", node_async(nodes::normalizePlan))
                    .addNode("initializeSession", node_async(nodes::initializeSession))
                    .addNode("planAgenda", node_async(nodes::planAgenda))
                    .addNode("discoverySearch", node_async(nodes::discoverySearch))
                    .addNode("intermediateSynthesis", node_async(nodes::intermediateSynthesis))
                    .addNode("gapAnalysis", node_async(nodes::gapAnalysis))
                    .addNode("routeIteration", node_async(nodes::routeIteration))
                    .addNode("focusedFollowup", node_async(nodes::focusedFollowup))
                    .addNode("convergeFinalize", node_async(nodes::convergeFinalize))
                    .addNode("writeArtifacts", node_async(nodes::writeArtifacts))
                    .addNode("markTaskCompleted", node_async(nodes::markTaskCompleted))
                    .addNode("publishCompletionEvents", node_async(nodes::publishCompletionEvents))
                    .addEdge(START, "hydrateTask")
                    .addEdge("hydrateTask", "normalizePlan")
                    .addEdge("normalizePlan", "initializeSession")
                    .addEdge("initializeSession", "planAgenda")
                    .addEdge("planAgenda", "discoverySearch")
                    .addEdge("discoverySearch", "intermediateSynthesis")
                    .addEdge("intermediateSynthesis", "gapAnalysis")
                    .addEdge("gapAnalysis", "routeIteration")
                    .addConditionalEdges("routeIteration",
                            edge_async(state -> shouldContinueResearchIteration(state) ? "followup" : "finalize"),
                            Map.of(
                                    "followup", "focusedFollowup",
                                    "finalize", "convergeFinalize"
                            ))
                    .addEdge("focusedFollowup", "discoverySearch")
                    .addEdge("convergeFinalize", "writeArtifacts")
                    .addEdge("writeArtifacts", "markTaskCompleted")
                    .addEdge("markTaskCompleted", "publishCompletionEvents")
                    .addEdge("publishCompletionEvents", END)
                    .compile(checkpointConfiguration.researchCompileConfig());
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile research graph", exception);
        }
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

    private static boolean hasPendingToolCalls(InteractionState state) {
        if (state.messages().isEmpty()) {
            return false;
        }
        AgentGraphMessage lastMessage = state.messages().get(state.messages().size() - 1);
        return lastMessage.type() == com.xg.platform.agent.core.AgentGraphMessageType.ASSISTANT
                && lastMessage.hasToolCalls();
    }

    private static boolean shouldContinueResearchIteration(ResearchTaskState state) {
        return state.<com.xg.platform.agent.core.research.execution.ResearchSessionState>researchSession()
                .map(session -> !session.converged()
                        && (session.gapLedger().stream().anyMatch(gap -> !gap.resolved())
                        || !session.pendingQueries().isEmpty()))
                .orElse(false);
    }
}
