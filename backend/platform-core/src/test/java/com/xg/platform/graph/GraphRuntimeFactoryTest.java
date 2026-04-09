package com.xg.platform.graph;

import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentGraphToolCall;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.contracts.artifact.ArtifactVisibility;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GraphRuntimeFactoryTest {

    @Test
    void executesInteractionGraphChatLoopStagesInOrder() {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CheckpointConfiguration checkpointConfiguration = new CheckpointConfiguration();
        GraphRuntimeFactory factory = new GraphRuntimeFactory(
                GraphRuntimeFactory.compileInteractionGraph(checkpointConfiguration, new InteractionGraphNodes() {
                    @Override
                    public Map<String, Object> loadShortTermMemory(InteractionState state) {
                        order.add("loadShortTermMemory");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> loadLongTermMemory(InteractionState state) {
                        order.add("loadLongTermMemory");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> loadDraftContext(InteractionState state) {
                        order.add("loadDraftContext");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> routeInteraction(InteractionState state) {
                        order.add("routeInteraction");
                        return Map.of(InteractionState.ROUTE_KIND, ChatRouteKind.CHAT);
                    }

                    @Override
                    public Map<String, Object> prepareAgentStep(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
                        order.add("prepareAgentStep");
                        return Map.of(
                                InteractionState.MESSAGES,
                                org.bsc.langgraph4j.state.AppenderChannel.ReplaceAllWith.of(List.of(
                                        AgentGraphMessage.assistant("assistant-0", "", List.of(new AgentGraphToolCall("call-1", "web_search", null)))
                                ))
                        );
                    }

                    @Override
                    public Map<String, Object> agent(InteractionState state) {
                        order.add("agent");
                        boolean hasToolResponse = state.messages().stream().anyMatch(message -> message.type() == com.xg.platform.agent.core.AgentGraphMessageType.TOOL);
                        if (!hasToolResponse) {
                            return Map.of();
                        }
                        return Map.of(
                                InteractionState.MESSAGES,
                                List.of(AgentGraphMessage.assistant("assistant-1", "final", List.of()))
                        );
                    }

                    @Override
                    public Map<String, Object> executeTools(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
                        order.add("executeTools");
                        return Map.of(
                                InteractionState.MESSAGES,
                                List.of(AgentGraphMessage.tool("tool-1", "web_search", "call-1", "{\"status\":\"ok\"}"))
                        );
                    }

                    @Override
                    public Map<String, Object> runScopingFrame(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> persistDraft(InteractionState state) {
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> persistAssistantMessage(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> persistTurnArtifacts(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
                        order.add("persistTurnArtifacts");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> publishTurnEvents(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
                        order.add("publishTurnEvents");
                        return Map.of(InteractionState.RESULT, "completed");
                    }
                }),
                GraphRuntimeFactory.compileResearchGraph(checkpointConfiguration, new NoOpResearchGraphNodes()),
                new RunEventConsumerRegistry()
        );

        factory.invokeInteraction(Map.of(
                        InteractionState.USER_ID, "user-1",
                        InteractionState.THREAD_ID, "thread-1",
                        InteractionState.REQUEST, new PostMessageRequest("hello", InteractionMode.CHAT, "gemini")
                ),
                "thread-1",
                event -> {
                });

        assertThat(order).contains(
                "loadShortTermMemory",
                "loadLongTermMemory",
                "loadDraftContext",
                "routeInteraction",
                "prepareAgentStep",
                "agent",
                "executeTools",
                "persistTurnArtifacts",
                "publishTurnEvents"
        );
        assertThat(order).hasSize(10);
        assertThat(order.indexOf("routeInteraction")).isGreaterThan(order.indexOf("loadShortTermMemory"));
        assertThat(order.indexOf("routeInteraction")).isGreaterThan(order.indexOf("loadLongTermMemory"));
        assertThat(order.indexOf("routeInteraction")).isGreaterThan(order.indexOf("loadDraftContext"));
        assertThat(order.indexOf("prepareAgentStep")).isGreaterThan(order.indexOf("routeInteraction"));
        assertThat(order.indexOf("executeTools")).isGreaterThan(order.indexOf("agent"));
        assertThat(order.lastIndexOf("agent")).isGreaterThan(order.indexOf("executeTools"));
        assertThat(order.indexOf("persistTurnArtifacts")).isGreaterThan(order.lastIndexOf("agent"));
        assertThat(order.indexOf("publishTurnEvents")).isGreaterThan(order.indexOf("persistTurnArtifacts"));
    }

    @Test
    void executesResearchGraphStagesWithExplicitIterationLoop() {
        List<String> order = new ArrayList<>();
        CheckpointConfiguration checkpointConfiguration = new CheckpointConfiguration();
        GraphRuntimeFactory factory = new GraphRuntimeFactory(
                GraphRuntimeFactory.compileInteractionGraph(checkpointConfiguration, new NoOpInteractionGraphNodes()),
                GraphRuntimeFactory.compileResearchGraph(checkpointConfiguration, new ResearchGraphNodes() {
                    @Override
                    public Map<String, Object> hydrateTask(ResearchTaskState state) {
                        order.add("hydrateTask");
                        return Map.of(ResearchTaskState.RESEARCH_BRIEF, "brief");
                    }

                    @Override
                    public Map<String, Object> normalizePlan(ResearchTaskState state) {
                        order.add("normalizePlan");
                        return Map.of(ResearchTaskState.APPROVED_PLAN, "approved");
                    }

                    @Override
                    public Map<String, Object> initializeSession(ResearchTaskState state) {
                        order.add("initializeSession");
                        return Map.of(
                                ResearchTaskState.RESEARCH_SESSION,
                                new com.xg.platform.agent.core.research.execution.ResearchSessionState(
                                        "brief",
                                        List.of(new com.xg.platform.contracts.research.ResearchAgendaItem("agenda-1", "One", "Objective", "high", "focus", false)),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of("query"),
                                        0,
                                        "initialize_session",
                                        false,
                                        "",
                                        Instant.now(),
                                        "",
                                        "",
                                        8,
                                        600_000L
                                )
                        );
                    }

                    @Override
                    public Map<String, Object> planAgenda(ResearchTaskState state) {
                        order.add("planAgenda");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> discoverySearch(ResearchTaskState state) {
                        order.add("discoverySearch");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> intermediateSynthesis(ResearchTaskState state) {
                        order.add("intermediateSynthesis");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> gapAnalysis(ResearchTaskState state) {
                        order.add("gapAnalysis");
                        int iterationNo = state.<com.xg.platform.agent.core.research.execution.ResearchSessionState>researchSession().orElseThrow().iterationNo() + 1;
                        return Map.of(
                                ResearchTaskState.RESEARCH_SESSION,
                                new com.xg.platform.agent.core.research.execution.ResearchSessionState(
                                        "brief",
                                        List.of(new com.xg.platform.contracts.research.ResearchAgendaItem("agenda-1", "One", "Objective", "high", "focus", true)),
                                        List.of(),
                                        List.of(),
                                        List.of(new com.xg.platform.contracts.research.ResearchFindingRecord("finding-1", "Finding", "summary", "medium", "", List.of(), false, null)),
                                        iterationNo >= 2 ? List.of() : List.of(new com.xg.platform.contracts.research.ResearchGapRecord("gap-1", iterationNo, "Gap", "Gap", "Strategy", false)),
                                        List.of(),
                                        List.of(),
                                        List.of("query"),
                                        iterationNo,
                                        "gap_analysis",
                                        iterationNo >= 2,
                                        "done",
                                        Instant.now(),
                                        iterationNo >= 2 ? "converged" : "",
                                        iterationNo >= 2 ? "Coverage is sufficient." : "",
                                        8,
                                        600_000L
                                )
                        );
                    }

                    @Override
                    public Map<String, Object> routeIteration(ResearchTaskState state) {
                        order.add("routeIteration");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> focusedFollowup(ResearchTaskState state) {
                        order.add("focusedFollowup");
                        return Map.of();
                    }

                    @Override
                    public Map<String, Object> convergeFinalize(ResearchTaskState state) {
                        order.add("convergeFinalize");
                        return Map.of(ResearchTaskState.FINAL_REPORT, "report");
                    }

                    @Override
                    public Map<String, Object> writeArtifacts(ResearchTaskState state) {
                        order.add("writeArtifacts");
                        return Map.of(ResearchTaskState.RESULT_ARTIFACT_ID, "artifact-1");
                    }

                    @Override
                    public Map<String, Object> markTaskCompleted(ResearchTaskState state) {
                        order.add("markTaskCompleted");
                        return Map.of(ResearchTaskState.CURRENT_STAGE, "completed");
                    }

                    @Override
                    public Map<String, Object> publishCompletionEvents(ResearchTaskState state) {
                        order.add("publishCompletionEvents");
                        return Map.of(ResearchTaskState.RESULT, "completed");
                    }
                }),
                new RunEventConsumerRegistry()
        );

        factory.invokeResearch(Map.of(
                        ResearchTaskState.USER_ID, "user-1",
                        ResearchTaskState.THREAD_ID, "thread-1",
                        ResearchTaskState.TASK_ID, "task-1"
                ),
                "task-1");

        assertThat(order).containsExactly(
                "hydrateTask",
                "normalizePlan",
                "initializeSession",
                "planAgenda",
                "discoverySearch",
                "intermediateSynthesis",
                "gapAnalysis",
                "routeIteration",
                "focusedFollowup",
                "discoverySearch",
                "intermediateSynthesis",
                "gapAnalysis",
                "routeIteration",
                "convergeFinalize",
                "writeArtifacts",
                "markTaskCompleted",
                "publishCompletionEvents"
        );
    }

    @Test
    void interactionGraphClonesArtifactStateWithoutObjectStreamSerialization() {
        CheckpointConfiguration checkpointConfiguration = new CheckpointConfiguration();
        GraphRuntimeFactory factory = new GraphRuntimeFactory(
                GraphRuntimeFactory.compileInteractionGraph(checkpointConfiguration, new NoOpInteractionGraphNodes() {
                    @Override
                    public Map<String, Object> loadShortTermMemory(InteractionState state) {
                        return Map.of(
                                InteractionState.ARTIFACTS,
                                List.of(new ArtifactRecord(
                                        "artifact-1",
                                        "user-1",
                                        "workspace-1",
                                        "thread-1",
                                        "notes.txt",
                                        ArtifactType.UPLOAD,
                                        ArtifactVisibility.USER_VISIBLE,
                                        WorkspaceArea.UPLOADS,
                                        "notes.txt",
                                        "text/plain",
                                        12L,
                                        Instant.parse("2026-04-08T12:00:00Z")
                                ))
                        );
                    }
                }),
                GraphRuntimeFactory.compileResearchGraph(checkpointConfiguration, new NoOpResearchGraphNodes()),
                new RunEventConsumerRegistry()
        );

        assertThatCode(() -> factory.invokeInteraction(
                Map.of(
                        InteractionState.USER_ID, "user-1",
                        InteractionState.THREAD_ID, "thread-1",
                        InteractionState.REQUEST, new PostMessageRequest("hello", InteractionMode.CHAT, "gemini")
                ),
                "thread-1",
                event -> {
                }
        )).doesNotThrowAnyException();
    }

    private static class NoOpInteractionGraphNodes implements InteractionGraphNodes {
        @Override
        public Map<String, Object> loadShortTermMemory(InteractionState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> loadLongTermMemory(InteractionState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> loadDraftContext(InteractionState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> routeInteraction(InteractionState state) {
            return Map.of(InteractionState.ROUTE_KIND, ChatRouteKind.CHAT);
        }

        @Override
        public Map<String, Object> prepareAgentStep(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
            return Map.of();
        }

        @Override
        public Map<String, Object> agent(InteractionState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> executeTools(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
            return Map.of();
        }

        @Override
        public Map<String, Object> runScopingFrame(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
            return Map.of();
        }

        @Override
        public Map<String, Object> persistDraft(InteractionState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> persistAssistantMessage(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
            return Map.of();
        }

        @Override
        public Map<String, Object> persistTurnArtifacts(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
            return Map.of();
        }

        @Override
        public Map<String, Object> publishTurnEvents(InteractionState state, java.util.function.Consumer<com.xg.platform.contracts.message.RunEvent> runEventConsumer) {
            return Map.of(InteractionState.RESULT, "completed");
        }
    }

    private static class NoOpResearchGraphNodes implements ResearchGraphNodes {
        @Override
        public Map<String, Object> hydrateTask(ResearchTaskState state) {
            return Map.of(ResearchTaskState.SKIP_EXECUTION, true);
        }

        @Override
        public Map<String, Object> normalizePlan(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> initializeSession(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> planAgenda(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> discoverySearch(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> intermediateSynthesis(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> gapAnalysis(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> routeIteration(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> focusedFollowup(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> convergeFinalize(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> writeArtifacts(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> markTaskCompleted(ResearchTaskState state) {
            return Map.of();
        }

        @Override
        public Map<String, Object> publishCompletionEvents(ResearchTaskState state) {
            return Map.of(ResearchTaskState.RESULT, "completed");
        }
    }
}
