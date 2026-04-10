package com.xg.platform.research.runtime;

import com.xg.platform.research.runtime.ResearchSessionState;
import com.xg.platform.shared.runtime.graph.CheckpointConfiguration;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class ResearchGraphDefinition {

    private ResearchGraphDefinition() {
    }

    public static CompiledGraph<ResearchTaskState> compile(CheckpointConfiguration checkpointConfiguration,
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

    private static boolean shouldContinueResearchIteration(ResearchTaskState state) {
        return state.<ResearchSessionState>researchSession()
                .map(session -> !session.converged()
                        && (session.gapLedger().stream().anyMatch(gap -> !gap.resolved())
                        || !session.pendingQueries().isEmpty()))
                .orElse(false);
    }
}
