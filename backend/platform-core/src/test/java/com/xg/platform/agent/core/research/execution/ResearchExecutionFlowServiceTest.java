package com.xg.platform.agent.core.research.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.CompressedFinding;
import com.xg.platform.agent.core.ResearchPlan;
import com.xg.platform.agent.core.ResearchUnit;
import com.xg.platform.agent.core.ResearchUnitResult;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryMessageRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryResearchTaskSnapshotRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryRunEventRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryTaskRepository;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport.InMemoryThreadRepository;
import com.xg.platform.contracts.agent.AgentCapability;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.message.ApprovedResearchPlan;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.research.ResearchFindingRecord;
import com.xg.platform.contracts.research.ResearchIterationRecord;
import com.xg.platform.contracts.research.ResearchReportSection;
import com.xg.platform.contracts.research.ResearchSourceKind;
import com.xg.platform.contracts.research.ResearchSourceRecord;
import com.xg.platform.contracts.research.ResearchTaskSnapshotRecord;
import com.xg.platform.contracts.task.TaskKind;
import com.xg.platform.contracts.task.TaskStatus;
import com.xg.platform.graph.ResearchTaskState;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolExecutionRequest;
import com.xg.platform.tools.ToolExecutionResult;
import com.xg.platform.tools.ToolGroup;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ResearchExecutionFlowServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void stagesResearchExecutionAndSkipsDuplicateArtifactWrite() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadRuntimeService threadRuntimeService = new ThreadRuntimeService(new InMemoryThreadRepository());
        String userId = "user-1";
        String threadId = threadRuntimeService.createThread(userId, "workspace-1", "Research Thread").threadId();
        String taskId = "task-1";
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        TaskRepository taskRepository = new InMemoryTaskRepository();
        RunEventRepository runEventRepository = new InMemoryRunEventRepository();
        MessageRepository messageRepository = new InMemoryMessageRepository();
        InMemoryResearchTaskSnapshotRepository researchTaskSnapshotRepository = new InMemoryResearchTaskSnapshotRepository();
        taskRepository.createQueuedTask(userId, threadId, taskId, "general-agent", TaskKind.RESEARCH, "AI chips", "Research AI chips", null);

        ResearchExecutionFlowService flowService = new ResearchExecutionFlowService(
                threadRuntimeService,
                taskRepository,
                runEventRepository,
                messageRepository,
                payload -> {
                },
                artifactService,
                workspaceManager,
                new StubResearchExecutionSupport(),
                new StubAgentTurnExecutionSupport(),
                new StubAgentToolService(objectMapper),
                researchTaskSnapshotRepository,
                objectMapper,
                false,
                8,
                600_000L
        );

        ThreadMemoryView memoryView = new ThreadMemoryView(threadId, "summary", List.of(), List.of(), null, null, null);
        Map<String, Object> stateMap = new HashMap<>(Map.of(
                ResearchTaskState.USER_ID, userId,
                ResearchTaskState.THREAD_ID, threadId,
                ResearchTaskState.TASK_ID, taskId,
                ResearchTaskState.PROVIDER_ID, "gemini",
                ResearchTaskState.RESEARCH_BRIEF, "Research Nvidia and AMD",
                ResearchTaskState.MEMORY_VIEW, memoryView,
                ResearchTaskState.SESSION_SUMMARY, memoryView.summary(),
                ResearchTaskState.LONG_TERM_MEMORY, "prefers concise reports"
        ));

        stateMap.putAll(flowService.normalizePlan(new ResearchTaskState(Map.copyOf(stateMap))));
        stateMap.putAll(flowService.initializeSession(new ResearchTaskState(Map.copyOf(stateMap))));
        stateMap.putAll(flowService.planAgenda(new ResearchTaskState(Map.copyOf(stateMap))));
        stateMap.putAll(flowService.discoverySearch(new ResearchTaskState(Map.copyOf(stateMap))));
        stateMap.putAll(flowService.intermediateSynthesis(new ResearchTaskState(Map.copyOf(stateMap))));
        stateMap.putAll(flowService.gapAnalysis(new ResearchTaskState(Map.copyOf(stateMap))));
        stateMap.putAll(flowService.convergeFinalize(new ResearchTaskState(Map.copyOf(stateMap))));
        Map<String, Object> writeResult = flowService.writeArtifacts(new ResearchTaskState(Map.copyOf(stateMap)));
        stateMap.putAll(writeResult);
        stateMap.putAll(flowService.markTaskCompleted(new ResearchTaskState(Map.copyOf(stateMap))));
        stateMap.putAll(flowService.publishCompletionEvents(new ResearchTaskState(Map.copyOf(stateMap))));

        assertThat(taskRepository.findTask(userId, threadId, taskId)).get()
                .extracting(task -> task.status(), task -> task.stage(), task -> task.resultArtifactId())
                .containsExactly(TaskStatus.COMPLETED, "completed", stateMap.get(ResearchTaskState.RESULT_ARTIFACT_ID));
        assertThat(artifactService.listArtifacts(userId, threadId))
                .extracting(artifact -> artifact.name(), artifact -> artifact.type())
                .contains(
                        tuple("research_plan.md", ArtifactType.NOTE),
                        tuple("response.md", ArtifactType.REPORT),
                        tuple("response.sources.json", ArtifactType.INTERMEDIATE_FILE),
                        tuple("response.citations.json", ArtifactType.INTERMEDIATE_FILE),
                        tuple("response.timeline.json", ArtifactType.INTERMEDIATE_FILE),
                        tuple("response.findings.json", ArtifactType.INTERMEDIATE_FILE),
                        tuple("response.plan.json", ArtifactType.INTERMEDIATE_FILE),
                        tuple("response.iterations.json", ArtifactType.INTERMEDIATE_FILE)
                );
        assertThat(messageRepository.listMessages(userId, threadId))
                .extracting(message -> message.content())
                .anySatisfy(content -> assertThat(content).contains("# Executive Summary"));
        List<RunEvent> events = runEventRepository.listEvents(userId, threadId, 120);
        assertThat(events).extracting(RunEvent::eventType)
                .contains("research.plan.created", "research.plan.ready", "research.iteration.started", "research.synthesis.updated", "research.gap.detected", "task.completed", "run.completed");
        List<ReportCitation> reportCitations = objectMapper.readValue(
                workspaceManager.resolvePath(userId, threadId, com.xg.platform.contracts.workspace.WorkspaceArea.OUTPUTS, taskId + "/response.citations.json").toFile(),
                new TypeReference<List<ReportCitation>>() {
                }
        );
        assertThat(reportCitations)
                .extracting(ReportCitation::citationLabel)
                .isNotEmpty();
        List<ResearchSourceRecord> sourceManifest = objectMapper.readValue(
                workspaceManager.resolvePath(userId, threadId, com.xg.platform.contracts.workspace.WorkspaceArea.OUTPUTS, taskId + "/response.sources.json").toFile(),
                new TypeReference<List<ResearchSourceRecord>>() {
                }
        );
        assertThat(sourceManifest)
                .extracting(source -> source.kind().name())
                .contains("WEB_PAGE");
        assertThat(researchTaskSnapshotRepository.findByTask(userId, threadId, taskId))
                .map(ResearchTaskSnapshotRecord::phase)
                .contains("converge_finalize");

        int artifactCount = artifactService.listArtifacts(userId, threadId).size();
        assertThat(flowService.writeArtifacts(new ResearchTaskState(Map.copyOf(stateMap)))).isEmpty();
        assertThat(artifactService.listArtifacts(userId, threadId)).hasSize(artifactCount);
    }

    private static final class StubAgentToolService implements AgentToolService {
        private final ObjectMapper objectMapper;

        private StubAgentToolService(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public List<ToolDescriptor> listAvailableTools(String userId) {
            return List.of(
                    new ToolDescriptor("web_search", "web_search", objectMapper.createObjectNode(), ToolGroup.SEARCH, "builtin"),
                    new ToolDescriptor("web_fetch", "web_fetch", objectMapper.createObjectNode(), ToolGroup.SEARCH, "builtin"),
                    new ToolDescriptor("research_reflect", "research_reflect", objectMapper.createObjectNode(), ToolGroup.WORKSPACE, "builtin")
            );
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            if ("web_search".equals(request.tool().name())) {
                var output = objectMapper.createObjectNode();
                output.putArray("results")
                        .add(objectMapper.createObjectNode()
                                .put("title", "Market overview")
                                .put("url", "https://example.com/market")
                                .put("snippet", "AI accelerator market overview"));
                return new ToolExecutionResult("web_search", output, false, "ok");
            }
            if ("web_fetch".equals(request.tool().name())) {
                return new ToolExecutionResult("web_fetch", objectMapper.createObjectNode()
                        .put("title", "Market overview")
                        .put("text", "Fetched market text"), false, "ok");
            }
            return new ToolExecutionResult("research_reflect", objectMapper.createObjectNode()
                    .put("summary", "Coverage is sufficient.")
                    .put("needsMoreEvidence", false)
                    .putArray("missingEvidence"), false, "ok");
        }
    }

    private static final class StubResearchExecutionSupport implements ResearchExecutionSupport {
        @Override
        public List<DocumentRecord> prepareResearchExecution(AgentExecutionRequest request,
                                                             com.xg.platform.agent.core.AgentOutputEmitter outputEmitter) {
            return List.of();
        }

        @Override
        public ResearchPlan createResearchPlan(ApprovedResearchPlan approvedPlan, List<DocumentRecord> documents) {
            return new ResearchPlan(
                    "Plan summary",
                    List.of(new ResearchUnit("step-1", "Compare vendors", "Compare Nvidia and AMD", "Nvidia AMD", false, false, "Competition"))
            );
        }

        @Override
        public ResearchUnitResult executeResearchUnit(String providerId,
                                                      AgentExecutionRequest request,
                                                      String researchBrief,
                                                      List<String> refinementNotes,
                                                      List<DocumentRecord> documents,
                                                      ResearchUnit unit,
                                                      com.xg.platform.agent.core.AgentOutputEmitter outputEmitter,
                                                      int stepIndex,
                                                      int totalSteps) {
            return new ResearchUnitResult(
                    unit.unitId(),
                    unit.title(),
                    unit.query(),
                    "notes",
                    "local conclusion",
                    List.of("[Nvidia Whitepaper, p.4]"),
                    List.of(new ResearchSourceRecord(
                            "document_chunk:nvidia-whitepaper:4",
                            ResearchSourceKind.DOCUMENT_CHUNK,
                            "Nvidia Whitepaper",
                            null,
                            "p.4",
                            "Benchmark comparison details.",
                            null,
                            unit.unitId()
                    ))
            );
        }

        @Override
        public List<CompressedFinding> compressFindings(String providerId,
                                                        AgentExecutionRequest request,
                                                        String researchBrief,
                                                        ResearchPlan researchPlan,
                                                        List<ResearchUnitResult> unitResults) {
            return List.of(new CompressedFinding("Finding", "Summary", "medium", List.of("source-1")));
        }

        @Override
        public String generateFinalReport(String providerId,
                                          AgentExecutionRequest request,
                                          String researchBrief,
                                          ResearchPlan researchPlan,
                                          List<CompressedFinding> findings,
                                          List<ReportCitation> citations,
                                          List<String> refinementNotes) {
            return "# Final Report\n\nNvidia and AMD remain the core comparison.";
        }
    }

    private static final class StubAgentTurnExecutionSupport implements AgentTurnExecutionSupport {
        @Override
        public String resolveProviderId(String requestedProviderId) {
            return "gemini";
        }

        @Override
        public String runTextTurn(String providerId, String modelOverride, String prompt, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String runModelLoop(String providerId,
                                   AgentExecutionRequest request,
                                   String prompt,
                                   List<com.xg.platform.tools.ToolDescriptor> availableTools,
                                   AgentOutputEmitter outputEmitter) {
            throw new UnsupportedOperationException();
        }
    }
}
