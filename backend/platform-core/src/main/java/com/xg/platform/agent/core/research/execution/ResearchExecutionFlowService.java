package com.xg.platform.agent.core.research.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.ResearchPlan;
import com.xg.platform.agent.core.ResearchUnit;
import com.xg.platform.agent.core.ToolExecutionGuard;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.contracts.artifact.ArtifactVisibility;
import com.xg.platform.contracts.artifact.RegisterArtifactCommand;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.message.ApprovedResearchPlan;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;
import com.xg.platform.contracts.message.ResearchPlanStep;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.message.RunEventType;
import com.xg.platform.contracts.message.ThreadFileReference;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.research.ResearchAgendaItem;
import com.xg.platform.contracts.research.ResearchEvidenceStatus;
import com.xg.platform.contracts.research.ResearchFindingRecord;
import com.xg.platform.contracts.research.ResearchGapRecord;
import com.xg.platform.contracts.research.ResearchIterationRecord;
import com.xg.platform.contracts.research.ResearchQueryRecord;
import com.xg.platform.contracts.research.ResearchReportBlock;
import com.xg.platform.contracts.research.ResearchReportSection;
import com.xg.platform.contracts.research.ResearchSourceKind;
import com.xg.platform.contracts.research.ResearchSourceRecord;
import com.xg.platform.contracts.task.TaskRecord;
import com.xg.platform.contracts.task.TaskStatus;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.graph.ResearchTaskState;
import com.xg.platform.runtime.MemoryEventPayload;
import com.xg.platform.runtime.MemoryEventPublisher;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.ResearchTaskSnapshotRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolExecutionRequest;
import com.xg.platform.tools.ToolExecutionResult;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

public class ResearchExecutionFlowService {

    private final ThreadRuntimeService threadRuntimeService;
    private final TaskRepository taskRepository;
    private final RunEventRepository runEventRepository;
    private final MessageRepository messageRepository;
    private final MemoryEventPublisher memoryEventPublisher;
    private final ArtifactService artifactService;
    private final WorkspaceManager workspaceManager;
    private final ResearchExecutionSupport researchExecutionSupport;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final AgentToolService agentToolService;
    private final ResearchTaskSnapshotRepository researchTaskSnapshotRepository;
    private final ObjectMapper objectMapper;
    private final boolean logAgentFlow;
    private final int maxIterations;
    private final long maxWallTimeMs;

    public ResearchExecutionFlowService(ThreadRuntimeService threadRuntimeService,
                                        TaskRepository taskRepository,
                                        RunEventRepository runEventRepository,
                                        MessageRepository messageRepository,
                                        MemoryEventPublisher memoryEventPublisher,
                                        ArtifactService artifactService,
                                        WorkspaceManager workspaceManager,
                                        ResearchExecutionSupport researchExecutionSupport,
                                        AgentTurnExecutionSupport agentTurnExecutionSupport,
                                        AgentToolService agentToolService,
                                        ResearchTaskSnapshotRepository researchTaskSnapshotRepository,
                                        ObjectMapper objectMapper,
                                        boolean logAgentFlow,
                                        int maxIterations,
                                        long maxWallTimeMs) {
        this.threadRuntimeService = threadRuntimeService;
        this.taskRepository = taskRepository;
        this.runEventRepository = runEventRepository;
        this.messageRepository = messageRepository;
        this.memoryEventPublisher = memoryEventPublisher;
        this.artifactService = artifactService;
        this.workspaceManager = workspaceManager;
        this.researchExecutionSupport = researchExecutionSupport;
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.agentToolService = agentToolService;
        this.researchTaskSnapshotRepository = researchTaskSnapshotRepository;
        this.objectMapper = objectMapper;
        this.logAgentFlow = logAgentFlow;
        this.maxIterations = Math.max(1, maxIterations);
        this.maxWallTimeMs = Math.max(60_000L, maxWallTimeMs);
    }

    public Map<String, Object> normalizePlan(ResearchTaskState state) {
        if (skip(state) || state.approvedPlan().isPresent()) return Map.of();
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        TaskRecord task = requireTask(userId, threadId, taskId);
        ApprovedResearchPlan approvedPlan = parseApprovedPlan(task.taskId(), task.title(), state.researchBrief().orElse(task.summary()));
        markTaskRunning(userId, threadId, taskId, summarize(approvedPlan.planSummary()), "plan", 20);
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_PLAN_APPROVED, approvedPlan);
        return Map.of(ResearchTaskState.APPROVED_PLAN, approvedPlan, ResearchTaskState.CURRENT_STAGE, "plan");
    }

    public Map<String, Object> initializeSession(ResearchTaskState state) {
        if (skip(state) || state.researchSession().isPresent()) return Map.of();
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        ApprovedResearchPlan approvedPlan = state.<ApprovedResearchPlan>approvedPlan().orElseThrow();
        ThreadMemoryView memoryView = state.<ThreadMemoryView>memoryView().orElseThrow();
        AgentExecutionRequest executionRequest = buildExecutionRequest(userId, threadId, taskId, approvedPlan.brief(), state.providerId().orElse(null), memoryView, state.longTermMemory().orElse(""));
        List<DocumentRecord> documents = researchExecutionSupport.prepareResearchExecution(executionRequest, new ForwardingEmitter(userId, threadId, taskId));
        ResearchPlan researchPlan = researchExecutionSupport.createResearchPlan(approvedPlan, documents);
        List<ResearchAgendaItem> agenda = agendaFromPlan(researchPlan);
        List<ResearchReportSection> reportPlan = reportPlanFromAgenda(agenda);
        List<String> pendingQueries = researchPlan.units().stream().map(ResearchUnit::query).filter(this::hasText).distinct().toList();
        ResearchSessionState session = new ResearchSessionState(
                approvedPlan.brief(),
                agenda,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                reportPlan,
                pendingQueries,
                0,
                "initialize_session",
                false,
                "",
                Instant.now(),
                "",
                "",
                maxIterations,
                maxWallTimeMs
        );
        ArtifactRecord planArtifact = writeMarkdownArtifact(userId, threadId, taskId + "/research/research_plan.md", "research_plan.md", renderPlanMarkdown(session), ArtifactType.NOTE, "text/markdown");
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_PLAN_CREATED, Map.of("taskId", taskId, "artifactId", planArtifact.artifactId(), "summary", researchPlan.summary(), "agendaItems", agenda.size()));
        markTaskRunning(userId, threadId, taskId, summarize(researchPlan.summary()), "plan_agenda", 30);
        persistSnapshot(userId, threadId, taskId, session, reportPlan, List.of(), List.of(), List.of(), List.of(), summarize(researchPlan.summary()));
        return Map.of(
                ResearchTaskState.RESEARCH_PLAN, researchPlan,
                ResearchTaskState.RESEARCH_SESSION, session,
                ResearchTaskState.PLAN, reportPlan,
                ResearchTaskState.PLAN_ARTIFACT_ID, planArtifact.artifactId(),
                ResearchTaskState.CURRENT_STAGE, "plan_agenda"
        );
    }

    public Map<String, Object> planAgenda(ResearchTaskState state) {
        if (skip(state)) return Map.of();
        ResearchSessionState session = session(state);
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        String providerId = agentTurnExecutionSupport.resolveProviderId(userId, state.providerId().orElse(null));
        String response = safeTextTurn(userId, providerId,
                "Return strict JSON with planSummary, agenda[{agendaId,title,objective,priority,coverageCriteria}], reportPlan[{sectionId,title,summary}], initialQueries.",
                """
                brief:
                %s

                current agenda:
                %s
                """.formatted(session.researchBrief(), renderAgenda(session.agenda())));
        List<ResearchAgendaItem> agenda = readAgenda(json(response).path("agenda"), session.agenda());
        List<ResearchReportSection> reportPlan = readReportPlan(json(response).path("reportPlan"), reportPlanFromAgenda(agenda));
        List<String> pendingQueries = readStrings(json(response).path("initialQueries"));
        ResearchSessionState updated = new ResearchSessionState(
                session.researchBrief(),
                agenda,
                session.queryHistory(),
                session.sourceLedger(),
                session.findingLedger(),
                session.gapLedger(),
                session.iterationNotes(),
                reportPlan,
                pendingQueries.isEmpty() ? session.pendingQueries() : pendingQueries,
                0,
                "plan_agenda",
                false,
                "",
                session.startedAt(),
                session.stopReason(),
                session.convergenceReason(),
                session.maxIterations(),
                session.maxWallTimeMs()
        );
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_PLAN_READY, eventPayload(taskId, 0, "plan_agenda", summarize(renderAgenda(agenda))));
        persistSnapshot(userId, threadId, taskId, updated, reportPlan, updated.iterationNotes(), updated.findingLedger(), updated.sourceLedger(), List.of(), summarize(renderAgenda(agenda)));
        return Map.of(ResearchTaskState.RESEARCH_SESSION, updated, ResearchTaskState.PLAN, reportPlan, ResearchTaskState.CURRENT_STAGE, "plan_agenda");
    }

    public Map<String, Object> discoverySearch(ResearchTaskState state) {
        if (skip(state)) return Map.of();
        ResearchSessionState session = session(state);
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        int iterationNo = session.iterationNo() + 1;
        List<String> queries = nextQueries(session);
        List<ResearchQueryRecord> queryHistory = new ArrayList<>(session.queryHistory());
        List<ResearchSourceRecord> sourceLedger = new ArrayList<>(session.sourceLedger());
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_ITERATION_STARTED, eventPayload(taskId, iterationNo, "discovery_search", "Starting iteration " + iterationNo));
        int index = 0;
        for (String query : queries) {
            index++;
            String queryId = "query-" + iterationNo + "-" + index;
            publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_QUERY_ISSUED, Map.of("taskId", taskId, "iterationNo", iterationNo, "phase", "discovery_search", "summary", summarize(query), "queryId", queryId, "query", query));
            List<SearchResultCandidate> candidates = readCandidates(executeTool(state, "web_search", objectMapper.createObjectNode().put("query", query).put("maxResults", 5)));
            int verifiedCount = 0;
            for (SearchResultCandidate candidate : candidates) {
                mergeSource(sourceLedger, source(candidate, iterationNo, query, ResearchSourceKind.WEB_RESULT, ResearchEvidenceStatus.CANDIDATE, "web_search", candidate.snippet()));
                publishSite(userId, threadId, taskId, iterationNo, candidate, "web_search");
            }
            for (SearchResultCandidate candidate : candidates.stream().limit(2).toList()) {
                try {
                    JsonNode fetch = executeTool(state, "web_fetch", objectMapper.createObjectNode().put("url", candidate.url()));
                    mergeSource(sourceLedger, source(candidate, iterationNo, query, ResearchSourceKind.WEB_PAGE, ResearchEvidenceStatus.VERIFIED, "web_fetch", fetch.path("text").asText(candidate.snippet())));
                    verifiedCount++;
                    publishSite(userId, threadId, taskId, iterationNo, candidate, "web_fetch");
                } catch (RuntimeException exception) {
                    publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_ACTIVITY, eventPayload(taskId, iterationNo, "discovery_search", "Skipped blocked source: " + candidate.url()));
                }
            }
            queryHistory.add(new ResearchQueryRecord(queryId, iterationNo, "discovery_search", query, iterationNo == 1 ? "broad_discovery" : "focused_search", verifiedCount > 0 ? "useful" : "thin", candidates.size(), verifiedCount));
        }
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_SOURCES_SELECTED, eventPayload(taskId, iterationNo, "discovery_search", "Selected sources for iteration " + iterationNo));
        markTaskRunning(userId, threadId, taskId, "Discovering and verifying sources", "discovery_search", 40 + ((iterationNo - 1) * 12));
        ResearchSessionState updated = new ResearchSessionState(
                session.researchBrief(),
                session.agenda(),
                queryHistory,
                sourceLedger,
                session.findingLedger(),
                session.gapLedger(),
                session.iterationNotes(),
                session.reportPlan(),
                List.of(),
                iterationNo,
                "discovery_search",
                false,
                session.convergenceSummary(),
                session.startedAt(),
                session.stopReason(),
                session.convergenceReason(),
                session.maxIterations(),
                session.maxWallTimeMs()
        );
        persistSnapshot(userId, threadId, taskId, updated, updated.reportPlan(), updated.iterationNotes(), updated.findingLedger(), sourceLedger, List.of(), "Selected sources for iteration " + iterationNo);
        return Map.of(ResearchTaskState.RESEARCH_SESSION, updated, ResearchTaskState.SOURCES, List.copyOf(sourceLedger), ResearchTaskState.CURRENT_STAGE, "discovery_search");
    }

    public Map<String, Object> intermediateSynthesis(ResearchTaskState state) {
        if (skip(state)) return Map.of();
        ResearchSessionState session = session(state);
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        String providerId = agentTurnExecutionSupport.resolveProviderId(userId, state.providerId().orElse(null));
        List<ResearchSourceRecord> verified = session.sourceLedger().stream().filter(s -> ResearchEvidenceStatus.VERIFIED.name().equalsIgnoreCase(text(s.evidenceStatus()))).limit(8).toList();
        String response = safeTextTurn(userId, providerId,
                "Return strict JSON with summary, confirmedFindings[{title,summary,confidence,scopeLimit,supportingSourceIds}], openQuestions, nextSearchIntent.",
                """
                brief:
                %s

                open agenda:
                %s

                verified evidence:
                %s
                """.formatted(session.researchBrief(), renderOpenAgenda(session.agenda()), renderEvidence(verified)));
        JsonNode node = json(response);
        String summary = fallback(text(node.path("summary").asText("")), "Evidence is accumulating, but coverage is still partial.");
        List<ResearchFindingRecord> generated = readFindings(node.path("confirmedFindings"), session.iterationNo(), summary, verified);
        List<String> openQuestions = readStrings(node.path("openQuestions"));
        List<String> nextSearchIntent = readStrings(node.path("nextSearchIntent"));
        if (generated.isEmpty() && !verified.isEmpty()) {
            generated = List.of(new ResearchFindingRecord("finding-" + session.iterationNo() + "-1", "Iteration " + session.iterationNo() + " synthesis", summary, verified.size() > 2 ? "medium" : "limited", "", List.of(verified.get(0).sourceId()), false, null));
        }
        List<ResearchFindingRecord> findingLedger = new ArrayList<>(session.findingLedger());
        findingLedger.addAll(generated);
        List<ResearchAgendaItem> agenda = coverAgenda(session.agenda(), generated.size());
        List<ResearchIterationRecord> iterations = new ArrayList<>(session.iterationNotes());
        iterations.add(new ResearchIterationRecord(session.iterationNo(), "intermediate_synthesis", summary, generated.stream().map(ResearchFindingRecord::title).toList(), openQuestions, nextSearchIntent, session.queryHistory().stream().filter(q -> q.iterationNo() == session.iterationNo()).map(ResearchQueryRecord::queryId).toList(), session.sourceLedger().stream().filter(s -> s.iterationNo() != null && s.iterationNo() == session.iterationNo()).map(ResearchSourceRecord::sourceId).distinct().toList()));
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_SYNTHESIS_UPDATED, eventPayload(taskId, session.iterationNo(), "intermediate_synthesis", summary));
        markTaskRunning(userId, threadId, taskId, summary, "intermediate_synthesis", 50 + ((session.iterationNo() - 1) * 12));
        ResearchSessionState updated = new ResearchSessionState(
                session.researchBrief(),
                agenda,
                session.queryHistory(),
                session.sourceLedger(),
                findingLedger,
                session.gapLedger(),
                iterations,
                session.reportPlan(),
                nextSearchIntent,
                session.iterationNo(),
                "intermediate_synthesis",
                false,
                session.convergenceSummary(),
                session.startedAt(),
                session.stopReason(),
                session.convergenceReason(),
                session.maxIterations(),
                session.maxWallTimeMs()
        );
        persistSnapshot(userId, threadId, taskId, updated, updated.reportPlan(), iterations, findingLedger, updated.sourceLedger(), List.of(), summary);
        return Map.of(ResearchTaskState.RESEARCH_SESSION, updated, ResearchTaskState.FINDINGS, List.copyOf(findingLedger), ResearchTaskState.ITERATIONS, List.copyOf(iterations), ResearchTaskState.CURRENT_STAGE, "intermediate_synthesis");
    }

    public Map<String, Object> gapAnalysis(ResearchTaskState state) {
        if (skip(state)) return Map.of();
        ResearchSessionState session = session(state);
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        ResearchIterationRecord latest = session.iterationNotes().isEmpty() ? new ResearchIterationRecord(session.iterationNo(), "intermediate_synthesis", "", List.of(), List.of(), List.of(), List.of(), List.of()) : session.iterationNotes().get(session.iterationNotes().size() - 1);
        ObjectNode reflectArgs = objectMapper.createObjectNode()
                .put("topic", session.researchBrief())
                .put("query", latest.nextSearchIntent().isEmpty() ? "" : latest.nextSearchIntent().get(0))
                .put("focus", renderOpenAgenda(session.agenda()))
                .put("evidenceSummary", latest.summary())
                .put("sourceCount", (int) session.sourceLedger().stream().filter(s -> ResearchEvidenceStatus.VERIFIED.name().equalsIgnoreCase(text(s.evidenceStatus()))).count())
                .put("stepIndex", session.iterationNo())
                .put("totalSteps", session.maxIterations());
        reflectArgs.set("openQuestions", objectMapper.valueToTree(latest.openQuestions()));
        reflectArgs.set("completedFindings", objectMapper.valueToTree(session.findingLedger().stream().map(ResearchFindingRecord::title).toList()));
        JsonNode reflection = executeTool(state, "research_reflect", reflectArgs);
        List<String> missingEvidence = readStrings(reflection.path("missingEvidence"));
        List<String> nextActions = readStrings(reflection.path("nextActions"));
        List<ResearchGapRecord> gaps = reconcileGaps(session.gapLedger(), latest.openQuestions(), missingEvidence, nextActions, session.iterationNo());
        boolean unresolvedGaps = gaps.stream().anyMatch(gap -> !gap.resolved());
        boolean needsMoreEvidence = reflection.path("needsMoreEvidence").asBoolean(unresolvedGaps);
        boolean agendaCovered = session.agenda().stream().filter(ResearchAgendaItem::covered).count() >= Math.max(1, session.agenda().size() - 1);
        boolean hasEnoughEvidence = !session.findingLedger().isEmpty()
                && session.sourceLedger().stream().anyMatch(source -> isVerifiedOrCited(source.evidenceStatus()));
        boolean iterationBudgetExhausted = session.iterationNo() >= session.maxIterations();
        boolean wallTimeBudgetExhausted = Duration.between(session.startedAt(), Instant.now()).toMillis() >= session.maxWallTimeMs();
        boolean modelConverged = (!needsMoreEvidence && hasEnoughEvidence && !unresolvedGaps)
                || (agendaCovered && hasEnoughEvidence && !unresolvedGaps);
        boolean converged = wallTimeBudgetExhausted || iterationBudgetExhausted || modelConverged;
        String stopReason = wallTimeBudgetExhausted
                ? "max_wall_time_exceeded"
                : iterationBudgetExhausted
                ? "max_iterations_exceeded"
                : modelConverged
                ? "converged"
                : "";
        String convergenceReason = wallTimeBudgetExhausted
                ? "Research stopped after reaching the wall-time budget."
                : iterationBudgetExhausted
                ? "Research stopped after reaching the iteration budget."
                : modelConverged
                ? "Agenda coverage and evidence sufficiency indicate the research can converge."
                : "";
        String summary = fallback(text(reflection.path("summary").asText("")), "Gap analysis complete.");
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_GAP_DETECTED, eventPayload(taskId, session.iterationNo(), "gap_analysis", summary));
        if (wallTimeBudgetExhausted || iterationBudgetExhausted) {
            publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_AGENT_BUDGET_EXHAUSTED, Map.of(
                    "taskId", taskId,
                    "iterationNo", session.iterationNo(),
                    "phase", "gap_analysis",
                    "summary", summary,
                    "stopReason", stopReason
            ));
        }
        if (converged) {
            publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_CONVERGED, eventPayload(taskId, session.iterationNo(), "gap_analysis", convergenceReason.isBlank() ? "Research converged." : convergenceReason));
            publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_AGENT_STOP_CONDITION_REACHED, Map.of(
                    "taskId", taskId,
                    "iterationNo", session.iterationNo(),
                    "phase", "gap_analysis",
                    "summary", summary,
                    "stopReason", stopReason,
                    "convergenceReason", convergenceReason
            ));
        }
        ResearchSessionState updated = new ResearchSessionState(
                session.researchBrief(),
                session.agenda(),
                session.queryHistory(),
                session.sourceLedger(),
                session.findingLedger(),
                gaps,
                session.iterationNotes(),
                session.reportPlan(),
                converged ? List.of() : nextActions,
                session.iterationNo(),
                "gap_analysis",
                converged,
                summary,
                session.startedAt(),
                stopReason,
                convergenceReason,
                session.maxIterations(),
                session.maxWallTimeMs()
        );
        persistSnapshot(userId, threadId, taskId, updated, updated.reportPlan(), updated.iterationNotes(), updated.findingLedger(), updated.sourceLedger(), List.of(), summary);
        return Map.of(ResearchTaskState.RESEARCH_SESSION, updated, ResearchTaskState.CURRENT_STAGE, "gap_analysis");
    }

    public Map<String, Object> focusedFollowup(ResearchTaskState state) {
        if (skip(state)) return Map.of();
        ResearchSessionState session = session(state);
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        String providerId = agentTurnExecutionSupport.resolveProviderId(userId, state.providerId().orElse(null));
        String response = safeTextTurn(userId, providerId, "Return strict JSON with queries:string[] and summary:string. Use only unresolved gaps and avoid scope expansion.", """
                brief:
                %s

                gaps:
                %s
                """.formatted(session.researchBrief(), renderOpenGaps(session.gapLedger())));
        List<String> queries = readStrings(json(response).path("queries"));
        if (queries.isEmpty()) queries = fallbackQueries(session);
        String summary = fallback(text(json(response).path("summary").asText("")), "Prepared focused follow-up queries.");
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_ITERATION_COMPLETED, eventPayload(taskId, session.iterationNo(), "focused_followup", summary));
        ResearchSessionState updated = new ResearchSessionState(
                session.researchBrief(),
                session.agenda(),
                session.queryHistory(),
                session.sourceLedger(),
                session.findingLedger(),
                session.gapLedger(),
                session.iterationNotes(),
                session.reportPlan(),
                queries,
                session.iterationNo(),
                "focused_followup",
                false,
                session.convergenceSummary(),
                session.startedAt(),
                session.stopReason(),
                session.convergenceReason(),
                session.maxIterations(),
                session.maxWallTimeMs()
        );
        persistSnapshot(userId, threadId, taskId, updated, updated.reportPlan(), updated.iterationNotes(), updated.findingLedger(), updated.sourceLedger(), List.of(), summary);
        return Map.of(ResearchTaskState.RESEARCH_SESSION, updated, ResearchTaskState.CURRENT_STAGE, "focused_followup");
    }

    public Map<String, Object> convergeFinalize(ResearchTaskState state) {
        if (skip(state) || state.finalReport().filter(this::hasText).isPresent()) return Map.of();
        ResearchSessionState session = session(state);
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        String providerId = agentTurnExecutionSupport.resolveProviderId(userId, state.providerId().orElse(null));
        List<ResearchSourceRecord> normalizedSources = session.sourceLedger().stream().map(ResearchEvidenceSupport::normalizeRecord).toList();
        List<ResearchSourceRecord> reportCandidateSources = selectReportCandidateSources(normalizedSources, session.findingLedger());
        List<ReportCitation> citationInventory = ResearchEvidenceSupport.buildReportCitationsFromSources(reportCandidateSources);
        String report = safeTextTurn(userId, providerId, "Write Markdown with Executive Summary, Key Findings, Evidence and Caveats, Conclusion, and Unverified / Needs More Research if needed. Use only provided citation labels like [W1].", """
                brief:
                %s

                plan:
                %s

                findings:
                %s

                citations:
                %s
                """.formatted(session.researchBrief(), renderPlanSections(session.reportPlan()), renderFindings(session.findingLedger()), ResearchEvidenceSupport.renderCitationInventory(citationInventory)));
        if (!hasText(report)) report = fallbackReport(session, citationInventory);
        report = ResearchEvidenceSupport.ensureReportCitations(report, citationInventory);
        report = enforceCitedParagraphs(report, citationInventory, session.gapLedger());
        List<ResearchReportBlock> blocks = buildBlocks(report, citationInventory);
        List<ReportCitation> citations = ResearchEvidenceSupport.markCitationUsage(citationInventory, report, blocks);
        List<ResearchFindingRecord> findings = markFindingsUsed(session.findingLedger(), citations);
        List<ResearchSourceRecord> sources = markSources(normalizedSources, findings, citations);
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_REPORT_CREATED, eventPayload(taskId, session.iterationNo(), "converge_finalize", summarize(report)));
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_REPORT_READY, Map.of("taskId", taskId, "iterationNo", session.iterationNo(), "phase", "converge_finalize", "summary", summarize(report), "citations", citations.stream().filter(ReportCitation::usedInReport).count(), "sources", sources.size()));
        for (String part : split(report, 180)) publishEvent(userId, threadId, taskId, RunEventType.MESSAGE_DELTA, Map.of("delta", part));
        ResearchSessionState updated = new ResearchSessionState(
                session.researchBrief(),
                session.agenda(),
                session.queryHistory(),
                sources,
                findings,
                session.gapLedger(),
                session.iterationNotes(),
                session.reportPlan(),
                List.of(),
                session.iterationNo(),
                "converge_finalize",
                true,
                session.convergenceSummary(),
                session.startedAt(),
                hasText(session.stopReason()) ? session.stopReason() : "converged",
                hasText(session.convergenceReason()) ? session.convergenceReason() : "Final report prepared from cited findings and verified evidence.",
                session.maxIterations(),
                session.maxWallTimeMs()
        );
        persistSnapshot(userId, threadId, taskId, updated, updated.reportPlan(), updated.iterationNotes(), findings, sources, citations, summarize(report));
        return Map.of(ResearchTaskState.RESEARCH_SESSION, updated, ResearchTaskState.FINAL_REPORT, report, ResearchTaskState.REPORT_BLOCKS, blocks, ResearchTaskState.SOURCES, sources, ResearchTaskState.CITATIONS, citations, ResearchTaskState.FINDINGS, findings, ResearchTaskState.ITERATIONS, session.iterationNotes(), ResearchTaskState.CURRENT_STAGE, "converge_finalize");
    }

    public Map<String, Object> writeArtifacts(ResearchTaskState state) {
        if (skip(state) || state.resultArtifactId().isPresent()) return Map.of();
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        ResearchSessionState session = session(state);
        String report = state.finalReport().orElseThrow();
        List<ResearchSourceRecord> sources = state.sources().isEmpty() ? session.sourceLedger() : state.sources();
        List<ReportCitation> citations = state.citations().isEmpty() ? ResearchEvidenceSupport.buildReportCitationsFromSources(sources) : state.citations();
        List<ResearchFindingRecord> findings = state.<List<ResearchFindingRecord>>findings().orElse(session.findingLedger());
        List<ResearchIterationRecord> iterations = state.iterations().isEmpty() ? session.iterationNotes() : state.iterations();
        List<ResearchReportSection> plan = state.plan().isEmpty() ? session.reportPlan() : state.plan();
        markTaskRunning(userId, threadId, taskId, "Writing research artifacts", "writing", 92);
        ArtifactRecord reportArtifact = writeMarkdownArtifact(userId, threadId, taskId + "/response.md", "response.md", report, ArtifactType.REPORT, "text/markdown");
        ArtifactRecord sourcesArtifact = writeJsonArtifact(userId, threadId, taskId + "/response.sources.json", "response.sources.json", sources);
        ArtifactRecord citationsArtifact = writeJsonArtifact(userId, threadId, taskId + "/response.citations.json", "response.citations.json", citations);
        ArtifactRecord timelineArtifact = writeJsonArtifact(userId, threadId, taskId + "/response.timeline.json", "response.timeline.json", buildTimelineView(session, iterations, citations));
        ArtifactRecord findingsArtifact = writeJsonArtifact(userId, threadId, taskId + "/response.findings.json", "response.findings.json", findings);
        ArtifactRecord planArtifact = writeJsonArtifact(userId, threadId, taskId + "/response.plan.json", "response.plan.json", plan);
        ArtifactRecord iterationsArtifact = writeJsonArtifact(userId, threadId, taskId + "/response.iterations.json", "response.iterations.json", iterations);
        persistMessage(userId, threadId, MessageRole.ASSISTANT, report, InteractionMode.DEEP_RESEARCH, taskId, taskId);
        for (ArtifactRecord artifact : List.of(reportArtifact, sourcesArtifact, citationsArtifact, timelineArtifact, findingsArtifact, planArtifact, iterationsArtifact)) publishEvent(userId, threadId, taskId, RunEventType.ARTIFACT_CREATED, artifact);
        persistSnapshot(userId, threadId, taskId, session, plan, iterations, findings, sources, citations, "Artifacts written");
        return Map.of(ResearchTaskState.RESULT_ARTIFACT_ID, reportArtifact.artifactId(), ResearchTaskState.SOURCES, sources, ResearchTaskState.CITATIONS, citations, ResearchTaskState.FINDINGS, findings, ResearchTaskState.ITERATIONS, iterations, ResearchTaskState.PLAN, plan, ResearchTaskState.CURRENT_STAGE, "writing");
    }

    public Map<String, Object> markTaskCompleted(ResearchTaskState state) {
        if (skip(state)) return Map.of();
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        TaskRecord task = requireTask(userId, threadId, taskId);
        if (task.status() == TaskStatus.COMPLETED) return Map.of(ResearchTaskState.CURRENT_STAGE, "completed");
        taskRepository.updateTask(userId, threadId, taskId, TaskStatus.COMPLETED, task.title(), safeSummary(task.summary(), state.finalReport().orElse("")), "completed", 100, task.linkedDraftId(), state.resultArtifactId().orElse(null));
        return Map.of(ResearchTaskState.CURRENT_STAGE, "completed");
    }

    public Map<String, Object> publishCompletionEvents(ResearchTaskState state) {
        if (skip(state) || state.completionEventsPublished().orElse(false)) return Map.of();
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        String artifactId = state.resultArtifactId().orElseThrow();
        String report = state.finalReport().orElseThrow();
        publishEvent(userId, threadId, taskId, RunEventType.TASK_STAGE_CHANGED, Map.of("taskId", taskId, "stage", "completed"));
        publishMemoryEvent(RunEventType.TASK_STAGE_CHANGED, userId, threadId, taskId, null);
        publishEvent(userId, threadId, taskId, RunEventType.TASK_PROGRESS, Map.of("taskId", taskId, "progress", 100));
        publishEvent(userId, threadId, taskId, RunEventType.TASK_COMPLETED, Map.of("taskId", taskId, "artifactId", artifactId));
        publishMemoryEvent(RunEventType.TASK_COMPLETED, userId, threadId, taskId, null);
        publishEvent(userId, threadId, taskId, RunEventType.RUN_COMPLETED, Map.of("summary", summarize(report), "sources", state.<ResearchSourceRecord>sources().stream().filter(s -> hasText(s.uri())).limit(8).map(s -> Map.of("kind", s.kind() == null ? "" : s.kind().name(), "title", fallback(text(s.title()), s.sourceId()), "domain", fallback(text(s.domain()), ""), "url", s.uri(), "verified", ResearchEvidenceStatus.VERIFIED.name().equalsIgnoreCase(text(s.evidenceStatus())) || ResearchEvidenceStatus.CITED.name().equalsIgnoreCase(text(s.evidenceStatus())), "usedInAnswer", s.citationIds() != null && !s.citationIds().isEmpty())).toList()));
        threadRuntimeService.touchThread(userId, threadId);
        return Map.of(ResearchTaskState.COMPLETION_EVENTS_PUBLISHED, true, ResearchTaskState.RESULT, "completed");
    }

    public void markFailed(ResearchTaskState state, RuntimeException exception) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String taskId = state.taskId().orElseThrow();
        TaskRecord task = requireTask(userId, threadId, taskId);
        if (task.status() == TaskStatus.CANCELLED || task.status() == TaskStatus.COMPLETED) return;
        taskRepository.updateTask(userId, threadId, taskId, TaskStatus.FAILED, task.title(), safeErrorMessage(exception), "failed", task.progress(), task.linkedDraftId(), task.resultArtifactId());
        publishEvent(userId, threadId, taskId, RunEventType.TASK_FAILED, Map.of("taskId", taskId, "error", safeErrorMessage(exception)));
        publishMemoryEvent(RunEventType.TASK_FAILED, userId, threadId, taskId, null);
        publishEvent(userId, threadId, taskId, RunEventType.RUN_FAILED, Map.of("error", safeErrorMessage(exception)));
    }

    private boolean skip(ResearchTaskState state) { return state.skipExecution().orElse(false); }
    private ResearchSessionState session(ResearchTaskState state) { return state.<ResearchSessionState>researchSession().orElseThrow(); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String text(String value) { return value == null ? "" : value.trim(); }
    private String fallback(String value, String fallback) { return hasText(value) ? value.trim() : fallback; }
    private String summarize(String value) { String v = value == null ? "" : value.trim().replaceAll("\\s+", " "); return v.length() > 120 ? v.substring(0, 120) + "..." : v; }
    private String safeSummary(String summary, String fallback) { return hasText(summary) ? summary : summarize(fallback); }
    private String safeErrorMessage(RuntimeException exception) { return hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName(); }
    private String domainOf(String url) { try { return java.net.URI.create(url).getHost(); } catch (Exception ignored) { return ""; } }
    private String truncate(String value, int limit) { String v = value == null ? "" : value.trim().replaceAll("\\s+", " "); return v.length() <= limit ? v : v.substring(0, limit) + "..."; }
    private Iterable<String> split(String value, int size) { List<String> out = new ArrayList<>(); for (int i = 0; i < value.length(); i += size) out.add(value.substring(i, Math.min(value.length(), i + size))); return out; }

    private TaskRecord requireTask(String userId, String threadId, String taskId) { return taskRepository.findTask(userId, threadId, taskId).orElseThrow(() -> new NoSuchElementException("Task not found: " + taskId)); }

    private void markTaskRunning(String userId, String threadId, String taskId, String summary, String stage, int progress) {
        TaskRecord task = requireTask(userId, threadId, taskId);
        taskRepository.updateTask(userId, threadId, taskId, TaskStatus.RUNNING, task.title(), summary, stage, progress, task.linkedDraftId(), task.resultArtifactId());
        publishEvent(userId, threadId, taskId, RunEventType.TASK_STAGE_CHANGED, Map.of("taskId", taskId, "stage", stage));
        publishMemoryEvent(RunEventType.TASK_STAGE_CHANGED, userId, threadId, taskId, null);
        publishEvent(userId, threadId, taskId, RunEventType.TASK_PROGRESS, Map.of("taskId", taskId, "progress", progress));
    }

    private AgentExecutionRequest buildExecutionRequest(String userId, String threadId, String runId, String message, String providerId, ThreadMemoryView memoryView, String longTermMemory) {
        List<ArtifactRecord> artifacts = artifactService.listArtifacts(userId, threadId);
        List<ThreadFileReference> uploadedFiles = artifacts.stream().filter(a -> a.type() == ArtifactType.UPLOAD).map(a -> new ThreadFileReference(a.name(), a.relativePath(), artifactService.resolveArtifactPath(userId, a).toString(), a.contentType(), a.sizeBytes())).toList();
        return new AgentExecutionRequest(userId, threadId, runId, message, null, providerId, List.of(), List.of(), "auto", artifacts, uploadedFiles, memoryView.recentMessages(), memoryView.summary(), longTermMemory);
    }

    private JsonNode executeTool(ResearchTaskState state, String toolName, ObjectNode arguments) {
        ToolDescriptor tool = agentToolService.requireTool(state.userId().orElseThrow(), toolName);
        ToolExecutionResult result = ToolExecutionGuard.execute(
                toolName,
                120_000L,
                () -> agentToolService.execute(new ToolExecutionRequest(
                        state.userId().orElseThrow(),
                        state.threadId().orElseThrow(),
                        state.taskId().orElseThrow(),
                        tool,
                        arguments,
                        null,
                        List.of()
                ))
        );
        return result.output();
    }

    private ArtifactRecord writeMarkdownArtifact(String userId, String threadId, String relativePath, String name, String content, ArtifactType type, String contentType) {
        Path path = writeOutput(userId, threadId, relativePath, content);
        return artifactService.register(new RegisterArtifactCommand(userId, threadRuntimeService.getThread(userId, threadId).workspaceId(), threadId, name, type, ArtifactVisibility.USER_VISIBLE, WorkspaceArea.OUTPUTS, workspaceManager.areaRoot(userId, threadId, WorkspaceArea.OUTPUTS).relativize(path).toString().replace('\\', '/'), contentType));
    }

    private Path writeOutput(String userId, String threadId, String relativePath, String content) {
        Path path = workspaceManager.resolvePath(userId, threadId, WorkspaceArea.OUTPUTS, relativePath);
        try { Files.createDirectories(path.getParent()); Files.writeString(path, content); return path; } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    private ArtifactRecord writeJsonArtifact(String userId, String threadId, String relativePath, String name, Object payload) {
        Path path = workspaceManager.resolvePath(userId, threadId, WorkspaceArea.OUTPUTS, relativePath);
        try { Files.createDirectories(path.getParent()); objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), payload); } catch (IOException exception) { throw new UncheckedIOException(exception); }
        return artifactService.register(new RegisterArtifactCommand(userId, threadRuntimeService.getThread(userId, threadId).workspaceId(), threadId, name, ArtifactType.INTERMEDIATE_FILE, ArtifactVisibility.USER_VISIBLE, WorkspaceArea.OUTPUTS, relativePath, "application/json"));
    }

    private MessageRecord persistMessage(String userId, String threadId, MessageRole role, String content, InteractionMode mode, String runId, String taskId) {
        return messageRepository.append(userId, new MessageRecord(UUID.randomUUID().toString(), threadId, role, content, mode, runId, taskId, Instant.now()));
    }

    private void publishEvent(String userId, String threadId, String runId, RunEventType type, Object payload) {
        runEventRepository.appendEvent(userId, threadId, new RunEvent(runId, threadId, type.value(), Instant.now(), payload));
    }

    private void publishMemoryEvent(RunEventType type, String userId, String threadId, String taskId, String messageId) {
        memoryEventPublisher.publish(new MemoryEventPayload(type.value(), userId, threadId, taskId, messageId, Instant.now()));
    }

    private ApprovedResearchPlan parseApprovedPlan(String taskId, String title, String taskInput) {
        if (hasText(taskInput)) { try { return objectMapper.readValue(taskInput, ApprovedResearchPlan.class); } catch (JsonProcessingException ignored) { } }
        return new ApprovedResearchPlan(taskId, 0, title, taskInput == null ? "" : taskInput, "", "", "", List.of(), summarize(taskInput), defaultPlanSteps(taskInput));
    }

    private List<ResearchPlanStep> defaultPlanSteps(String latestUserInput) {
        String query = hasText(latestUserInput) ? latestUserInput.trim() : "research topic";
        return List.of(
                new ResearchPlanStep("step-1", "Define the problem and baseline context", "Clarify the topic, scope, and baseline facts", query, true, true, "Definitions, scope, and baseline context"),
                new ResearchPlanStep("step-2", "Gather evidence and competing views", "Collect the strongest evidence, disagreements, and caveats", query + " evidence competing views", true, true, "Evidence, disagreements, and caveats"),
                new ResearchPlanStep("step-3", "Synthesize implications", "Summarize implications, limits, and unanswered questions", query + " implications limitations unanswered questions", true, true, "Implications, limits, and unanswered questions")
        );
    }

    private List<ResearchAgendaItem> agendaFromPlan(ResearchPlan plan) {
        List<ResearchAgendaItem> agenda = new ArrayList<>(); int index = 0;
        for (ResearchUnit unit : plan.units()) agenda.add(new ResearchAgendaItem(fallback(unit.unitId(), "agenda-" + (++index)), fallback(unit.title(), "Agenda " + index), fallback(unit.objective(), unit.title()), index == 1 ? "high" : index == 2 ? "medium" : "low", fallback(unit.outputFocus(), "Evidence and synthesis"), false));
        return List.copyOf(agenda);
    }

    private List<ResearchReportSection> reportPlanFromAgenda(List<ResearchAgendaItem> agenda) {
        List<ResearchReportSection> sections = new ArrayList<>(); sections.add(new ResearchReportSection("executive-summary", "Executive Summary", "Summarize the outcome."));
        int index = 0; for (ResearchAgendaItem item : agenda) sections.add(new ResearchReportSection("section-" + (++index), item.title(), item.coverageCriteria()));
        sections.add(new ResearchReportSection("caveats", "Evidence and Caveats", "Highlight limits and disagreements."));
        sections.add(new ResearchReportSection("conclusion", "Conclusion", "Wrap up the report."));
        return List.copyOf(sections);
    }

    private String renderPlanMarkdown(ResearchSessionState session) { return "# Research Plan\n\n" + renderAgenda(session.agenda()) + "\n\n## Report Plan\n\n" + renderPlanSections(session.reportPlan()); }
    private String renderAgenda(List<ResearchAgendaItem> agenda) { return agenda.isEmpty() ? "- none" : agenda.stream().map(item -> "- " + item.title() + ": " + fallback(item.coverageCriteria(), item.objective())).collect(Collectors.joining(System.lineSeparator())); }
    private String renderPlanSections(List<ResearchReportSection> sections) { return sections.isEmpty() ? "- none" : sections.stream().map(section -> "- " + section.title() + ": " + fallback(section.summary(), "")).collect(Collectors.joining(System.lineSeparator())); }
    private String renderOpenAgenda(List<ResearchAgendaItem> agenda) { return agenda.stream().filter(item -> !item.covered()).map(item -> "- " + item.title() + ": " + fallback(item.objective(), item.coverageCriteria())).collect(Collectors.joining(System.lineSeparator())); }
    private String renderEvidence(List<ResearchSourceRecord> sources) { return sources.isEmpty() ? "- none" : sources.stream().map(source -> "- " + fallback(source.title(), source.sourceId()) + ": " + fallback(source.snippet(), source.uri())).collect(Collectors.joining(System.lineSeparator())); }
    private String renderFindings(List<ResearchFindingRecord> findings) { return findings.isEmpty() ? "- none" : findings.stream().map(f -> "- " + f.title() + ": " + f.summary() + " | " + String.join(", ", f.supportingSourceIds())).collect(Collectors.joining(System.lineSeparator())); }
    private String renderOpenGaps(List<ResearchGapRecord> gaps) { return gaps.stream().filter(g -> !g.resolved()).map(g -> "- " + g.topic() + ": " + g.strategy()).collect(Collectors.joining(System.lineSeparator())); }

    private JsonNode json(String text) { try { return hasText(text) ? objectMapper.readTree(text) : objectMapper.createObjectNode(); } catch (Exception exception) { return objectMapper.createObjectNode(); } }
    private String safeTextTurn(String userId, String providerId, String prompt, String userMessage) { try { return agentTurnExecutionSupport.runTextTurn(userId, providerId, null, prompt, userMessage); } catch (RuntimeException exception) { return ""; } }
    private List<String> readStrings(JsonNode node) {
        try {
            List<String> values = objectMapper.convertValue(node, new TypeReference<List<String>>() {});
            if (values == null) return List.of();
            return values.stream().filter(this::hasText).map(String::trim).toList();
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
    }

    private List<ResearchAgendaItem> readAgenda(JsonNode node, List<ResearchAgendaItem> fallback) {
        List<ResearchAgendaItem> out = new ArrayList<>(); int index = 0;
        if (node.isArray()) for (JsonNode item : node) out.add(new ResearchAgendaItem(fallback(item.path("agendaId").asText(""), "agenda-" + (++index)), fallback(item.path("title").asText(""), "Agenda " + index), fallback(item.path("objective").asText(""), ""), fallback(item.path("priority").asText(""), "medium"), fallback(item.path("coverageCriteria").asText(""), ""), false));
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    private List<ResearchReportSection> readReportPlan(JsonNode node, List<ResearchReportSection> fallback) {
        List<ResearchReportSection> out = new ArrayList<>(); int index = 0;
        if (node.isArray()) for (JsonNode item : node) out.add(new ResearchReportSection(fallback(item.path("sectionId").asText(""), "section-" + (++index)), fallback(item.path("title").asText(""), "Section " + index), fallback(item.path("summary").asText(""), "")));
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    private List<SearchResultCandidate> readCandidates(JsonNode node) {
        List<SearchResultCandidate> out = new ArrayList<>();
        if (node.path("results").isArray()) for (JsonNode item : node.path("results")) if (hasText(item.path("url").asText(""))) out.add(new SearchResultCandidate(fallback(item.path("title").asText(""), item.path("url").asText("")), item.path("url").asText(""), item.path("snippet").asText("")));
        return List.copyOf(out);
    }

    private ResearchSourceRecord source(SearchResultCandidate candidate, int iterationNo, String query, ResearchSourceKind kind, ResearchEvidenceStatus status, String method, String snippet) {
        return new ResearchSourceRecord((kind == ResearchSourceKind.WEB_PAGE ? "web_page:" : "web_result:") + candidate.url(), kind, fallback(candidate.title(), candidate.url()), candidate.url(), null, truncate(snippet, 240), fallback(domainOf(candidate.url()), ""), null, null, iterationNo, query, status.name().toLowerCase(), method, List.of(), List.of(), truncate(snippet, 120));
    }

    private void publishSite(String userId, String threadId, String taskId, int iterationNo, SearchResultCandidate candidate, String sourceType) {
        publishEvent(userId, threadId, taskId, RunEventType.RESEARCH_SITE_DISCOVERED, Map.of("taskId", taskId, "iterationNo", iterationNo, "phase", "discovery_search", "summary", candidate.title(), "sourceId", candidate.url(), "url", candidate.url(), "title", candidate.title(), "domain", fallback(domainOf(candidate.url()), ""), "sourceType", sourceType));
    }

    private List<String> nextQueries(ResearchSessionState session) { return !session.pendingQueries().isEmpty() ? session.pendingQueries().stream().filter(this::hasText).limit(3).toList() : fallbackQueries(session); }
    private List<String> fallbackQueries(ResearchSessionState session) { List<String> queries = session.gapLedger().stream().filter(g -> !g.resolved()).map(g -> session.researchBrief() + " " + g.topic()).limit(3).toList(); return queries.isEmpty() ? session.agenda().stream().filter(a -> !a.covered()).map(a -> session.researchBrief() + " " + a.title()).limit(3).toList() : queries; }

    private List<ResearchFindingRecord> readFindings(JsonNode node, int iterationNo, String summary, List<ResearchSourceRecord> fallbackSources) {
        List<ResearchFindingRecord> out = new ArrayList<>(); int index = 0;
        if (node.isArray()) for (JsonNode item : node) out.add(new ResearchFindingRecord("finding-" + iterationNo + "-" + (++index), fallback(item.path("title").asText(""), "Finding " + index), fallback(item.path("summary").asText(""), summary), fallback(item.path("confidence").asText(""), "medium"), fallback(item.path("scopeLimit").asText(""), ""), readStrings(item.path("supportingSourceIds")), false, null));
        if (out.isEmpty() && !fallbackSources.isEmpty()) out.add(new ResearchFindingRecord("finding-" + iterationNo + "-1", "Iteration " + iterationNo + " synthesis", summary, fallbackSources.size() > 2 ? "medium" : "limited", "", List.of(fallbackSources.get(0).sourceId()), false, null));
        return List.copyOf(out);
    }

    private List<ResearchAgendaItem> coverAgenda(List<ResearchAgendaItem> agenda, int count) {
        List<ResearchAgendaItem> out = new ArrayList<>(); int remaining = count;
        for (ResearchAgendaItem item : agenda) { boolean covered = item.covered() || remaining-- > 0; out.add(new ResearchAgendaItem(item.agendaId(), item.title(), item.objective(), item.priority(), item.coverageCriteria(), covered)); }
        return List.copyOf(out);
    }

    private List<ResearchGapRecord> reconcileGaps(List<ResearchGapRecord> existing, List<String> openQuestions, List<String> missingEvidence, List<String> nextActions, int iterationNo) {
        LinkedHashMap<String, ResearchGapRecord> merged = new LinkedHashMap<>(); for (ResearchGapRecord gap : existing) merged.put(gap.topic(), gap);
        List<String> open = new ArrayList<>(); open.addAll(openQuestions); open.addAll(missingEvidence); int index = 0;
        for (String topic : open) if (hasText(topic)) merged.put(topic, new ResearchGapRecord(merged.containsKey(topic) ? merged.get(topic).gapId() : "gap-" + iterationNo + "-" + (++index), iterationNo, topic, topic, nextActions.isEmpty() ? "Collect more evidence" : nextActions.get(Math.min(index - 1, nextActions.size() - 1)), false));
        return merged.values().stream().map(g -> new ResearchGapRecord(g.gapId(), g.iterationNo(), g.topic(), g.reason(), g.strategy(), !open.contains(g.topic()))).toList();
    }

    private void mergeSource(List<ResearchSourceRecord> ledger, ResearchSourceRecord incoming) {
        for (int i = 0; i < ledger.size(); i++) if (ledger.get(i).sourceId().equals(incoming.sourceId())) { ledger.set(i, rank(ledger.get(i).evidenceStatus()) >= rank(incoming.evidenceStatus()) ? ledger.get(i) : incoming); return; }
        ledger.add(incoming);
    }

    private int rank(String status) { if (ResearchEvidenceStatus.CITED.name().equalsIgnoreCase(status)) return 3; if (ResearchEvidenceStatus.VERIFIED.name().equalsIgnoreCase(status)) return 2; return 1; }
    private boolean isVerifiedOrCited(String status) { return rank(status) >= 2; }

    private List<ResearchReportBlock> buildBlocks(String markdown, List<ReportCitation> citations) {
        List<ResearchReportBlock> out = new ArrayList<>(); int index = 0;
        for (String paragraph : markdown.split("\\R\\R+")) { String p = paragraph.trim(); if (!hasText(p)) continue; String blockId = "block-" + (++index); String paragraphId = "paragraph-" + index; List<String> citationIds = citations.stream().filter(c -> hasText(c.citationLabel()) && p.contains("[" + c.citationLabel() + "]")).map(ReportCitation::citationId).distinct().toList(); out.add(new ResearchReportBlock(blockId, paragraphId, p, citationIds)); }
        return List.copyOf(out);
    }

    private List<ResearchFindingRecord> markFindingsUsed(List<ResearchFindingRecord> findings, List<ReportCitation> citations) {
        LinkedHashSet<String> sourceIds = citations.stream().filter(ReportCitation::usedInReport).map(ReportCitation::sourceId).collect(Collectors.toCollection(LinkedHashSet::new));
        return findings.stream().map(f -> new ResearchFindingRecord(f.findingId(), f.title(), f.summary(), f.confidence(), f.scopeLimit(), f.supportingSourceIds(), f.supportingSourceIds().stream().anyMatch(sourceIds::contains), f.reportSectionId())).toList();
    }

    private List<ResearchSourceRecord> markSources(List<ResearchSourceRecord> sources, List<ResearchFindingRecord> findings, List<ReportCitation> citations) {
        Map<String, List<String>> sourceToCitations = citations.stream().filter(ReportCitation::usedInReport).collect(Collectors.groupingBy(ReportCitation::sourceId, LinkedHashMap::new, Collectors.mapping(ReportCitation::citationId, Collectors.toList())));
        Map<String, List<String>> sourceToFindings = findings.stream().flatMap(f -> f.supportingSourceIds().stream().map(id -> Map.entry(id, f.findingId()))).collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Map<String, String> sourceToAnchorText = citations.stream()
                .filter(ReportCitation::usedInReport)
                .filter(citation -> hasText(citation.anchorText()))
                .collect(Collectors.toMap(ReportCitation::sourceId, ReportCitation::anchorText, (left, right) -> left, LinkedHashMap::new));
        return sources.stream().map(source -> new ResearchSourceRecord(source.sourceId(), source.kind(), source.title(), source.uri(), source.locator(), source.snippet(), source.domain(), source.unitId(), source.citationLabel(), source.iterationNo(), source.discoveryQuery(), sourceToCitations.containsKey(source.sourceId()) ? ResearchEvidenceStatus.CITED.name().toLowerCase() : source.evidenceStatus(), source.verificationMethod(), sourceToFindings.getOrDefault(source.sourceId(), source.supportingFindingIds()), sourceToCitations.getOrDefault(source.sourceId(), source.citationIds()), sourceToAnchorText.getOrDefault(source.sourceId(), source.anchorText()))).toList();
    }

    private List<ResearchSourceRecord> selectReportCandidateSources(List<ResearchSourceRecord> sources,
                                                                   List<ResearchFindingRecord> findings) {
        LinkedHashSet<String> supportedSourceIds = findings.stream()
                .flatMap(finding -> finding.supportingSourceIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ResearchSourceRecord> filtered = sources.stream()
                .filter(source -> isVerifiedOrCited(source.evidenceStatus()))
                .filter(source -> supportedSourceIds.isEmpty() || supportedSourceIds.contains(source.sourceId()))
                .toList();
        return filtered.isEmpty()
                ? sources.stream().filter(source -> isVerifiedOrCited(source.evidenceStatus())).toList()
                : filtered;
    }

    private String enforceCitedParagraphs(String report,
                                          List<ReportCitation> citations,
                                          List<ResearchGapRecord> gaps) {
        if (!hasText(report)) {
            return report;
        }
        LinkedHashSet<String> citationTokens = citations.stream()
                .map(ReportCitation::citationLabel)
                .filter(this::hasText)
                .map(label -> "[" + label + "]")
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> preserved = new ArrayList<>();
        List<String> unverified = new ArrayList<>();
        String currentHeading = "";
        for (String paragraph : report.split("\\R\\R+")) {
            String normalized = paragraph.trim();
            if (!hasText(normalized)) {
                continue;
            }
            if (normalized.startsWith("#")) {
                currentHeading = normalized.replaceFirst("^#+\\s*", "").trim().toLowerCase();
                preserved.add(normalized);
                continue;
            }
            boolean cited = citationTokens.stream().anyMatch(normalized::contains);
            boolean alreadyUnverified = currentHeading.contains("unverified") || currentHeading.contains("needs more research") || currentHeading.equals("sources");
            if (cited || alreadyUnverified) {
                preserved.add(normalized);
            } else {
                unverified.add(normalized);
            }
        }
        if (gaps.stream().anyMatch(gap -> !gap.resolved())) {
            gaps.stream()
                    .filter(gap -> !gap.resolved())
                    .map(gap -> "- " + gap.topic() + ": " + gap.reason())
                    .forEach(unverified::add);
        }
        if (unverified.isEmpty()) {
            return String.join(System.lineSeparator() + System.lineSeparator(), preserved).trim();
        }
        if (preserved.stream().noneMatch(paragraph -> paragraph.equalsIgnoreCase("## Unverified / Needs More Research"))) {
            preserved.add("## Unverified / Needs More Research");
        }
        preserved.addAll(unverified);
        return String.join(System.lineSeparator() + System.lineSeparator(), preserved).trim();
    }

    private List<Map<String, Object>> buildTimelineView(ResearchSessionState session,
                                                        List<ResearchIterationRecord> iterations,
                                                        List<ReportCitation> citations) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(Map.of(
                "iterationNo", 0,
                "phase", "plan_agenda",
                "summary", summarize(renderPlanSections(session.reportPlan()))
        ));
        for (ResearchIterationRecord iteration : iterations) {
            timeline.add(Map.of(
                    "iterationNo", iteration.iterationNo(),
                    "phase", iteration.phase(),
                    "summary", iteration.summary(),
                    "confirmedFindings", iteration.confirmedFindings(),
                    "openQuestions", iteration.openQuestions(),
                    "nextSearchIntent", iteration.nextSearchIntent()
            ));
        }
        timeline.add(Map.of(
                "iterationNo", session.iterationNo(),
                "phase", "converge_finalize",
                "summary", hasText(session.convergenceReason()) ? session.convergenceReason() : session.convergenceSummary(),
                "citationsUsed", citations.stream().filter(ReportCitation::usedInReport).count(),
                "stopReason", session.stopReason()
        ));
        return List.copyOf(timeline);
    }

    private void persistSnapshot(String userId,
                                 String threadId,
                                 String taskId,
                                 ResearchSessionState session,
                                 List<ResearchReportSection> plan,
                                 List<ResearchIterationRecord> iterations,
                                 List<ResearchFindingRecord> findings,
                                 List<ResearchSourceRecord> sources,
                                 List<ReportCitation> citations,
                                 String summary) {
        researchTaskSnapshotRepository.save(userId, new com.xg.platform.contracts.research.ResearchTaskSnapshotRecord(
                taskId,
                threadId,
                session.phase(),
                session.iterationNo(),
                plan,
                iterations,
                findings,
                sources,
                citations,
                summary,
                session.converged(),
                Instant.now()
        ));
    }

    private String fallbackReport(ResearchSessionState session, List<ReportCitation> citations) {
        StringBuilder builder = new StringBuilder("# Executive Summary\n\nThis report summarizes the evidence collected for: ").append(session.researchBrief()).append("\n\n## Key Findings\n\n");
        for (ResearchFindingRecord finding : session.findingLedger()) { builder.append("- ").append(finding.title()).append(": ").append(finding.summary()); String citation = citations.stream().filter(c -> finding.supportingSourceIds().contains(c.sourceId())).findFirst().map(c -> " [" + c.citationLabel() + "]").orElse(""); builder.append(citation).append('\n'); }
        builder.append("\n## Evidence and Caveats\n\n").append(hasText(session.convergenceSummary()) ? session.convergenceSummary() : "Evidence quality varies across sources.").append("\n\n## Conclusion\n\nKeep conclusions grounded in the cited evidence.");
        return builder.toString().trim();
    }

    private Map<String, Object> eventPayload(String taskId, int iterationNo, String phase, String summary) { return Map.of("taskId", taskId, "iterationNo", iterationNo, "phase", phase, "summary", summary); }

    private final class ForwardingEmitter implements AgentOutputEmitter {
        private final String userId; private final String threadId; private final String runId;
        private ForwardingEmitter(String userId, String threadId, String runId) { this.userId = userId; this.threadId = threadId; this.runId = runId; }
        @Override public void emitText(String delta) { }
        @Override public void emitEvent(RunEventType eventType, Object payload) { publishEvent(userId, threadId, runId, eventType, payload); }
    }

    private record SearchResultCandidate(String title, String url, String snippet) implements Serializable { }
}
