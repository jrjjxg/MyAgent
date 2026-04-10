package com.xg.platform.conversation.runtime;

import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.conversation.domain.ConversationRouteKind;
import com.xg.platform.shared.runtime.graph.CheckpointConfiguration;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class ConversationGraphDefinition {

    private ConversationGraphDefinition() {
    }

    public static CompiledGraph<InteractionState> compile(CheckpointConfiguration checkpointConfiguration,
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
                                    .map(ConversationRouteKind.class::cast)
                                    .filter(kind -> kind == ConversationRouteKind.RESEARCH_DRAFT)
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

    private static boolean hasPendingToolCalls(InteractionState state) {
        if (state.messages().isEmpty()) {
            return false;
        }
        AgentGraphMessage lastMessage = state.messages().get(state.messages().size() - 1);
        return lastMessage.type() == com.xg.platform.agent.core.AgentGraphMessageType.ASSISTANT
                && lastMessage.hasToolCalls();
    }
}
