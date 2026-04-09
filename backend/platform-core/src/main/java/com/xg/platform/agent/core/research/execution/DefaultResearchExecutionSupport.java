package com.xg.platform.agent.core.research.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.AgentExecutionRequest;
import com.xg.platform.agent.core.AgentMode;
import com.xg.platform.agent.core.AgentOutputEmitter;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.CompressedFinding;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.agent.core.ResearchPlan;
import com.xg.platform.agent.core.ResearchUnit;
import com.xg.platform.agent.core.ResearchUnitResult;
import com.xg.platform.agent.core.ToolExecutionGuard;
import com.xg.platform.agent.core.ToolUseLimits;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.message.ApprovedResearchPlan;
import com.xg.platform.contracts.message.ResearchPlanStep;
import com.xg.platform.contracts.message.RunEventType;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.research.ResearchSourceKind;
import com.xg.platform.contracts.research.ResearchSourceRecord;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.memory.ChunkIndexStore;
import com.xg.platform.memory.ContextAssembler;
import com.xg.platform.memory.DocumentChunk;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.memory.RetrievedChunk;
import com.xg.platform.tools.SkillRuntimeSnapshot;
import com.xg.platform.tools.SkillRegistry;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolExecutionRequest;
import com.xg.platform.tools.ToolExecutionResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultResearchExecutionSupport implements ResearchExecutionSupport {

    private static final Logger logger = Logger.getLogger(DefaultResearchExecutionSupport.class.getName());

    private final SkillRegistry skillRegistry;
    private final AgentToolService agentToolService;
    private final DocumentStore documentStore;
    private final DocumentIngestService documentIngestService;
    private final ChunkIndexStore chunkIndexStore;
    private final ContextAssembler contextAssembler;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final ObjectMapper objectMapper;
    private final boolean logAgentFlow;
    private final boolean logModelResponses;
    private final ResearchUnitAgentRunner researchUnitAgentRunner;
    private final int maxToolCalls;
    private final int maxSearchCalls;
    private final int maxFetchCalls;
    private final int reflectionAfterSearches;
    private final int minVerifiedSources;
    private final long timeoutMs;

    public DefaultResearchExecutionSupport(SkillRegistry skillRegistry,
                                           AgentToolService agentToolService,
                                           DocumentStore documentStore,
                                           DocumentIngestService documentIngestService,
                                           ChunkIndexStore chunkIndexStore,
                                           ContextAssembler contextAssembler,
                                           AgentTurnExecutionSupport agentTurnExecutionSupport,
                                           ObjectMapper objectMapper,
                                           boolean logAgentFlow,
                                           boolean logModelResponses) {
        this(
                skillRegistry,
                agentToolService,
                documentStore,
                documentIngestService,
                chunkIndexStore,
                contextAssembler,
                agentTurnExecutionSupport,
                objectMapper,
                logAgentFlow,
                logModelResponses,
                6,
                3,
                3,
                1,
                1,
                120_000L
        );
    }

    public DefaultResearchExecutionSupport(SkillRegistry skillRegistry,
                                           AgentToolService agentToolService,
                                           DocumentStore documentStore,
                                           DocumentIngestService documentIngestService,
                                           ChunkIndexStore chunkIndexStore,
                                           ContextAssembler contextAssembler,
                                           AgentTurnExecutionSupport agentTurnExecutionSupport,
                                           ObjectMapper objectMapper,
                                           boolean logAgentFlow,
                                           boolean logModelResponses,
                                           int maxToolCalls,
                                           int maxSearchCalls,
                                           int maxFetchCalls,
                                           int reflectionAfterSearches,
                                           int minVerifiedSources,
                                           long timeoutMs) {
        this.skillRegistry = skillRegistry;
        this.agentToolService = agentToolService;
        this.documentStore = documentStore;
        this.documentIngestService = documentIngestService;
        this.chunkIndexStore = chunkIndexStore;
        this.contextAssembler = contextAssembler;
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.objectMapper = objectMapper;
        this.logAgentFlow = logAgentFlow;
        this.logModelResponses = logModelResponses;
        this.researchUnitAgentRunner = new ResearchUnitAgentRunner(agentTurnExecutionSupport);
        this.maxToolCalls = maxToolCalls;
        this.maxSearchCalls = maxSearchCalls;
        this.maxFetchCalls = maxFetchCalls;
        this.reflectionAfterSearches = reflectionAfterSearches;
        this.minVerifiedSources = minVerifiedSources;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public List<DocumentRecord> prepareResearchExecution(AgentExecutionRequest request, AgentOutputEmitter outputEmitter) {
        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "prepare");
        List<DocumentRecord> documents = documentStore.listDocuments(request.userId(), request.threadId());
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "prepare");

        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "ensure-ingest");
        documents = documentIngestService.ensureReadyDocuments(request.userId(), request.threadId(), documents, request.runId(), outputEmitter);
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "ensure-ingest");
        return documents;
    }

    @Override
    public ResearchPlan createResearchPlan(ApprovedResearchPlan approvedPlan, List<DocumentRecord> documents) {
        List<ResearchUnit> units = new ArrayList<>();
        int index = 1;
        for (ResearchPlanStep step : approvedPlan.planSteps()) {
            units.add(new ResearchUnit(
                    step.stepId() == null || step.stepId().isBlank() ? "step-" + index : step.stepId(),
                    textOrFallback(step.title(), "Research step " + index),
                    textOrFallback(step.objective(), textOrFallback(step.title(), "Research step " + index)),
                    textOrFallback(step.query(), approvedPlan.brief()),
                    step.useDocuments(),
                    step.useWeb(),
                    textOrFallback(step.outputFocus(), "Key findings and evidence")
            ));
            index++;
        }
        if (!units.isEmpty()) {
            return new ResearchPlan(textOrFallback(approvedPlan.planSummary(), summarize(approvedPlan.brief())), List.copyOf(units));
        }
        return fallbackResearchPlan(approvedPlan.brief(), documents);
    }

    @Override
    public ResearchUnitResult executeResearchUnit(String providerId,
                                                  AgentExecutionRequest request,
                                                  String researchBrief,
                                                  List<String> refinementNotes,
                                                  List<DocumentRecord> documents,
                                                  ResearchUnit unit,
                                                  AgentOutputEmitter outputEmitter,
                                                  int stepIndex,
                                                  int totalSteps) {
        SkillRuntimeSnapshot snapshot = snapshotFor(request);
        List<SkillDescriptor> availableSkills = skillRegistry.listDescriptors(request.userId());
        AgentExecutionRequest unitRequest = withRuntimeContext(
                request,
                snapshot,
                new ToolUseLimits(
                        maxToolCalls,
                        maxSearchCalls,
                        maxFetchCalls,
                        reflectionAfterSearches,
                        minVerifiedSources,
                        timeoutMs
                ),
                List.of()
        );
        List<RetrievedChunk> retrievedChunks = unit.useDocuments()
                ? retrieveChunksForQuery(unitRequest, documents, unit.query(), 6)
                : List.of();
        List<String> sources = new ArrayList<>();
        List<ResearchSourceRecord> sourceRecords = new ArrayList<>();
        StringBuilder evidence = new StringBuilder();
        if (!retrievedChunks.isEmpty()) {
            evidence.append("Document evidence:\n");
            for (RetrievedChunk retrievedChunk : retrievedChunks) {
                ResearchSourceRecord sourceRecord = documentSourceRecord(unit.unitId(), retrievedChunk.chunk());
                String source = renderSourceReference(sourceRecord);
                sources.add(source);
                sourceRecords.add(sourceRecord);
                evidence.append("- ").append(source).append(": ")
                        .append(truncate(retrievedChunk.chunk().text(), 500))
                        .append("\n");
            }
            emitActivity(outputEmitter, request.runId(), unit.unitId(), "Collected " + retrievedChunks.size() + " relevant document excerpts.");
        }
        if (evidence.isEmpty()) {
            evidence.append("No strong evidence was retrieved for this unit yet. Use tools to gather evidence before concluding.");
        }

        List<ToolDescriptor> unitTools = researchAgentTools(request.userId(), unit.useWeb());
        CapturingEmitter capturingEmitter = new CapturingEmitter(outputEmitter, unit.unitId(), request.runId());
        String response = researchUnitAgentRunner.run(
                providerId,
                unitRequest,
                buildResearchUnitPrompt(researchBrief, refinementNotes, unit, evidence.toString(), availableSkills),
                unitTools,
                capturingEmitter
        );
        emitActivity(outputEmitter, request.runId(), unit.unitId(), "Synthesizing the evidence collected for step " + stepIndex + " of " + totalSteps + ".");
        sourceRecords.addAll(capturingEmitter.candidateSourceRecords());
        sourceRecords.addAll(capturingEmitter.verifiedSourceRecords());
        sources.addAll(capturingEmitter.candidateSources());
        sources.addAll(capturingEmitter.verifiedSources());
        List<ResearchSourceRecord> dedupedSourceRecords = dedupeSourceRecords(sourceRecords);
        boolean insufficientVerifiedWebEvidence = unit.useWeb()
                && retrievedChunks.isEmpty()
                && dedupedSourceRecords.stream().filter(sourceRecord -> sourceRecord.kind() == ResearchSourceKind.WEB_PAGE).count() < minVerifiedSources;
        outputEmitter.emitEvent(RunEventType.RESEARCH_AGENT_STOP_CONDITION_REACHED, Map.of(
                "runId", request.runId(),
                "stepId", unit.unitId(),
                "verifiedSources", dedupedSourceRecords.stream().filter(sourceRecord -> sourceRecord.kind() == ResearchSourceKind.WEB_PAGE).count(),
                "toolCalls", unitRequest.toolUseLimits() == null ? 0 : unitRequest.toolUseLimits().totalCalls(),
                "reason", insufficientVerifiedWebEvidence ? "insufficient-verified-sources" : "model-finished"
        ));
        try {
            JsonNode node = objectMapper.readTree(response);
            String notes = textOrFallback(node, "notes", truncate(evidence.toString(), 1500));
            String localConclusion = textOrFallback(node, "localConclusion", summarize(evidence.toString()));
            if (insufficientVerifiedWebEvidence) {
                notes = notes + System.lineSeparator() + "Evidence limits: verified web coverage stayed below the required threshold.";
                localConclusion = localConclusion + " Evidence remains limited because verified web coverage stayed below the required threshold.";
            }
            return new ResearchUnitResult(
                    unit.unitId(),
                    unit.title(),
                    unit.query(),
                    notes,
                    localConclusion,
                    mergeSources(sources, node.path("sources")),
                    dedupedSourceRecords
            );
        } catch (Exception ignored) {
            String notes = truncate(evidence.toString(), 1500);
            String conclusion = summarize(evidence.toString());
            if (insufficientVerifiedWebEvidence) {
                notes = notes + System.lineSeparator() + "Evidence limits: verified web coverage stayed below the required threshold.";
                conclusion = conclusion + " Evidence remains limited because verified web coverage stayed below the required threshold.";
            }
            return new ResearchUnitResult(
                    unit.unitId(),
                    unit.title(),
                    unit.query(),
                    notes,
                    conclusion,
                    List.copyOf(new LinkedHashSet<>(sources)),
                    dedupedSourceRecords
            );
        }
    }

    @Override
    public List<CompressedFinding> compressFindings(String providerId,
                                                    AgentExecutionRequest request,
                                                    String researchBrief,
                                                    ResearchPlan researchPlan,
                                                    List<ResearchUnitResult> unitResults) {
        String response = runTextTurn(
                request.userId(),
                providerId,
                """
                        You are compressing deep research unit outputs into final findings.
                        Return strict JSON with key findings containing an array of objects with:
                        heading: string
                        summary: string
                        evidenceStrength: string
                        supportingSources: string[]
                        Keep between 3 and 6 findings.
                        """,
                """
                        Research brief:
                        %s

                        Research plan summary:
                        %s

                        Unit outputs:
                        %s
                        """.formatted(
                        researchBrief,
                        researchPlan.summary(),
                        renderUnitOutputsForPrompt(unitResults)
                )
        );
        try {
            JsonNode node = objectMapper.readTree(response);
            List<CompressedFinding> findings = new ArrayList<>();
            for (JsonNode findingNode : node.path("findings")) {
                findings.add(new CompressedFinding(
                        textOrFallback(findingNode, "heading", "Finding"),
                        textOrFallback(findingNode, "summary", ""),
                        textOrFallback(findingNode, "evidenceStrength", "medium"),
                        readStringList(findingNode.path("supportingSources"))
                ));
            }
            if (!findings.isEmpty()) {
                return List.copyOf(findings);
            }
        } catch (Exception ignored) {
        }
        return unitResults.stream()
                .map(unitResult -> new CompressedFinding(
                        unitResult.title(),
                        unitResult.localConclusion(),
                        unitResult.sources().size() >= 3 ? "medium" : "limited",
                        unitResult.sources()
                ))
                .toList();
    }

    @Override
    public String generateFinalReport(String providerId,
                                      AgentExecutionRequest request,
                                      String researchBrief,
                                      ResearchPlan researchPlan,
                                      List<CompressedFinding> findings,
                                      List<ReportCitation> citations,
                                      List<String> refinementNotes) {
        return runTextTurn(
                request.userId(),
                providerId,
                """
                        You are writing the final report for a deep research task.
                        Produce Markdown with:
                        - concise executive summary
                        - key findings
                        - evidence and caveats
                        - conclusion
                        Keep claims grounded and include sources inline where available.
                        Use only the provided citation labels.
                        Format citations as standalone labels like [W1] or [D2].
                        Do not invent labels or cite sources that are not in the citation inventory.
                        """,
                """
                        Research brief:
                        %s

                        Refinements:
                        %s

                        Research plan:
                        %s

                        Compressed findings:
                        %s

                        Citation inventory:
                        %s
                        """.formatted(
                        researchBrief,
                        refinementNotes.isEmpty() ? "- none" : String.join(System.lineSeparator(), refinementNotes),
                        renderResearchPlan(researchPlan),
                        renderFindings(findings),
                        ResearchEvidenceSupport.renderCitationInventory(citations)
                )
        );
    }

    private ResearchPlan fallbackResearchPlan(String researchBrief, List<DocumentRecord> documents) {
        boolean hasDocuments = !documents.isEmpty();
        List<ResearchUnit> units = List.of(
                new ResearchUnit("unit-1", "Baseline understanding", "Clarify the topic and central questions", researchBrief, hasDocuments, true, "Definitions, scope, and baseline facts"),
                new ResearchUnit("unit-2", "Evidence and competing views", "Collect supporting and conflicting evidence", researchBrief + " competing views evidence", hasDocuments, true, "Evidence, disagreements, and caveats"),
                new ResearchUnit("unit-3", "Practical implications", "Summarize implications, limitations, and next steps", researchBrief + " implications limitations", hasDocuments, true, "Implications, limits, and recommendations")
        );
        return new ResearchPlan("Research the topic from baseline, evidence, and implications perspectives.", units);
    }

    private int appendWebEvidence(AgentExecutionRequest request,
                                  ResearchUnit unit,
                                  String query,
                                  AgentOutputEmitter outputEmitter,
                                  StringBuilder evidence,
                                  List<String> sources,
                                  List<ResearchSourceRecord> sourceRecords) {
        emitActivity(outputEmitter, request.runId(), unit.unitId(), "Searching the web for: " + query);
        JsonNode searchOutput = executeTool(request, "web_search", objectMapper.createObjectNode()
                .put("query", query)
                .put("maxResults", 3));
        evidence.append("\nWeb search results:\n");
        List<String> topUrls = new ArrayList<>();
        for (JsonNode resultNode : searchOutput.path("results")) {
            String url = resultNode.path("url").asText("");
            if (url.isBlank()) {
                continue;
            }
            topUrls.add(url);
            String title = resultNode.path("title").asText(url);
            ResearchSourceRecord sourceRecord = webSearchSourceRecord(unit.unitId(), title, url, resultNode.path("snippet").asText(""));
            String source = renderSourceReference(sourceRecord);
            sources.add(source);
            sourceRecords.add(sourceRecord);
            evidence.append("- ").append(source).append(": ")
                    .append(resultNode.path("snippet").asText(""))
                    .append("\n");
            outputEmitter.emitEvent(RunEventType.RESEARCH_SITE_DISCOVERED, sourceEventPayload(request, unit, sourceRecord, "web_search"));
        }
        int fetchCount = 0;
        for (String url : topUrls.stream().distinct().limit(2).toList()) {
            try {
                JsonNode fetchOutput = executeTool(request, "web_fetch", objectMapper.createObjectNode().put("url", url));
                ResearchSourceRecord fetchRecord = webPageSourceRecord(
                        unit.unitId(),
                        fetchOutput.path("title").asText(url),
                        url,
                        fetchOutput.path("text").asText("")
                );
                String source = renderSourceReference(fetchRecord);
                sources.add(source);
                sourceRecords.add(fetchRecord);
                evidence.append("\nFetched page: ").append(source).append("\n")
                        .append(truncate(fetchOutput.path("text").asText(""), 1200))
                        .append("\n");
                outputEmitter.emitEvent(RunEventType.RESEARCH_SITE_DISCOVERED, sourceEventPayload(request, unit, fetchRecord, "web_fetch"));
                fetchCount++;
            } catch (RuntimeException exception) {
                emitActivity(
                        outputEmitter,
                        request.runId(),
                        unit.unitId(),
                        "Skipped a blocked source page: " + url + " (" + safeMessage(exception) + ")",
                        "source-skip",
                        List.of()
                );
                evidence.append("\nSkipped page: ").append(url).append("\n")
                        .append("- Fetch failed: ").append(safeMessage(exception)).append("\n");
            }
        }
        if (fetchCount > 0) {
            emitActivity(outputEmitter, request.runId(), unit.unitId(), "Read " + fetchCount + " source pages for this step.");
        }
        return fetchCount;
    }

    private void emitActivity(AgentOutputEmitter outputEmitter,
                              String runId,
                              String stepId,
                              String summary) {
        emitActivity(outputEmitter, runId, stepId, summary, "activity", List.of());
    }

    private void emitActivity(AgentOutputEmitter outputEmitter,
                              String runId,
                              String stepId,
                              String summary,
                              String kind,
                              List<String> sourceIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", runId);
        payload.put("stepId", stepId);
        payload.put("summary", summary);
        payload.put("kind", kind);
        if (sourceIds != null && !sourceIds.isEmpty()) {
            payload.put("sourceIds", List.copyOf(sourceIds));
        }
        outputEmitter.emitEvent(RunEventType.RESEARCH_ACTIVITY, payload);
    }

    private JsonNode reflectOnEvidence(AgentExecutionRequest request,
                                       ResearchUnit unit,
                                       List<String> refinementNotes,
                                       CharSequence evidence,
                                       List<ResearchSourceRecord> sourceRecords,
                                       int stepIndex,
                                       int totalSteps) {
        var arguments = objectMapper.createObjectNode();
        arguments.put("topic", unit.title());
        arguments.put("query", unit.query());
        arguments.put("evidenceSummary", truncate(evidence == null ? "" : evidence.toString(), 2500));
        arguments.put("sourceCount", dedupeSourceRecords(sourceRecords).size());
        arguments.put("stepIndex", stepIndex);
        arguments.put("totalSteps", totalSteps);
        arguments.put("focus", unit.outputFocus());
        arguments.set("openQuestions", toArrayNode(deriveOpenQuestions(unit, refinementNotes)));
        arguments.set("completedFindings", toArrayNode(sourceRecords.stream()
                .map(ResearchSourceRecord::title)
                .filter(title -> title != null && !title.isBlank())
                .distinct()
                .limit(4)
                .toList()));
        return executeTool(request, "research_reflect", arguments);
    }

    private void appendReflectionEvidence(StringBuilder evidence, JsonNode reflection) {
        String summary = reflection.path("summary").asText("").trim();
        if (summary.isBlank()) {
            return;
        }
        evidence.append("\nResearch reflection:\n")
                .append("- ")
                .append(summary)
                .append("\n");
        List<String> nextActions = readStringList(reflection.path("nextActions"));
        if (!nextActions.isEmpty()) {
            evidence.append("Next actions:\n");
            nextActions.stream()
                    .limit(3)
                    .forEach(action -> evidence.append("- ").append(action).append("\n"));
        }
    }

    private String buildFollowUpQuery(ResearchUnit unit, JsonNode reflection) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String baseQuery = textOrFallback(unit.query(), "");
        if (!baseQuery.isBlank()) {
            terms.add(baseQuery);
        }
        readStringList(reflection.path("focusAreas")).stream()
                .limit(2)
                .forEach(terms::add);
        if (terms.size() < 2) {
            readStringList(reflection.path("missingEvidence")).stream()
                    .map(this::normalizeFollowUpTerm)
                    .filter(term -> !term.isBlank())
                    .limit(1)
                    .forEach(terms::add);
        }
        String followUpQuery = String.join(" ", terms).trim();
        return truncate(followUpQuery, 180);
    }

    private String normalizeFollowUpTerm(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("Unresolved question:", "")
                .replace("Current evidence summary is still shallow; fetch fuller content from the strongest sources.", "")
                .replace("More corroborating sources are needed before final synthesis.", "")
                .replace("No source-backed evidence has been collected yet.", "")
                .trim();
    }

    private List<String> deriveOpenQuestions(ResearchUnit unit, List<String> refinementNotes) {
        LinkedHashSet<String> questions = new LinkedHashSet<>();
        if (!isBlank(unit.outputFocus())) {
            questions.add(unit.outputFocus());
        }
        for (String refinementNote : refinementNotes) {
            if (refinementNote != null && !refinementNote.isBlank()) {
                questions.add(refinementNote.trim());
            }
            if (questions.size() >= 3) {
                break;
            }
        }
        return List.copyOf(questions);
    }

    private JsonNode toArrayNode(List<String> values) {
        var array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private Map<String, Object> sourceEventPayload(AgentExecutionRequest request,
                                                   ResearchUnit unit,
                                                   ResearchSourceRecord sourceRecord,
                                                   String sourceType) {
        return Map.of(
                "runId", request.runId(),
                "stepId", unit.unitId(),
                "sourceId", sourceRecord.sourceId(),
                "sourceKind", sourceRecord.kind().name(),
                "url", textOrFallback(sourceRecord.uri(), ""),
                "title", textOrFallback(sourceRecord.title(), ""),
                "domain", textOrFallback(sourceRecord.domain(), ""),
                "snippet", textOrFallback(sourceRecord.snippet(), ""),
                "sourceType", sourceType
        );
    }

    private List<RetrievedChunk> retrieveChunksForQuery(AgentExecutionRequest request,
                                                        List<DocumentRecord> documents,
                                                        String query,
                                                        int limit) {
        List<RetrievedChunk> retrievedChunks = contextAssembler.retrieve(
                query,
                documents,
                document -> chunkIndexStore.readChunkIndex(request.userId(), request.threadId(), "documents/%s/chunks.json".formatted(document.documentId())),
                limit
        );
        if (!retrievedChunks.isEmpty()) {
            return retrievedChunks;
        }
        List<RetrievedChunk> fallbackChunks = new ArrayList<>();
        for (DocumentRecord document : documents) {
            List<DocumentChunk> chunks = chunkIndexStore.readChunkIndex(
                    request.userId(),
                    request.threadId(),
                    "documents/%s/chunks.json".formatted(document.documentId())
            );
            for (int index = 0; index < Math.min(2, chunks.size()); index++) {
                fallbackChunks.add(new RetrievedChunk(chunks.get(index), Math.max(1, 10 - index)));
            }
        }
        return fallbackChunks.stream()
                .sorted(Comparator.comparingInt(RetrievedChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    private JsonNode executeTool(AgentExecutionRequest request, String toolName, JsonNode arguments) {
        ToolDescriptor toolDescriptor = agentToolService.requireTool(request.userId(), toolName);
        ToolExecutionResult executionResult = ToolExecutionGuard.execute(
                toolName,
                request.toolUseLimits() == null ? 120_000L : request.toolUseLimits().timeoutMs(),
                () -> agentToolService.execute(new ToolExecutionRequest(
                        request.userId(),
                        request.threadId(),
                        request.runId(),
                        toolDescriptor,
                        arguments,
                        request.skillRuntimeSnapshot(),
                        request.activeSkillIds()
                ))
        );
        return executionResult.output();
    }

    private String runTextTurn(String userId, String providerId, String prompt, String userMessage) {
        logFlow(() -> "runTextTurn provider=" + providerId + " promptChars=" + prompt.length() + " userMessage=" + summarize(userMessage));
        String response = agentTurnExecutionSupport.runTextTurn(userId, providerId, prompt, userMessage);
        logModelResult(() -> "runTextTurn result provider=" + providerId
                + " hasText=" + !response.isBlank()
                + " text=" + summarize(response));
        if (!response.isBlank()) {
            return response;
        }
        throw new IllegalStateException("Model returned no text");
    }

    private SkillRuntimeSnapshot snapshotFor(AgentExecutionRequest request) {
        return request.skillRuntimeSnapshot() == null
                ? skillRegistry.snapshotForUser(request.userId())
                : request.skillRuntimeSnapshot();
    }

    private AgentExecutionRequest withRuntimeContext(AgentExecutionRequest request,
                                                     SkillRuntimeSnapshot snapshot,
                                                     ToolUseLimits toolUseLimits,
                                                     List<String> activeSkillIds) {
        return new AgentExecutionRequest(
                request.userId(),
                request.threadId(),
                request.runId(),
                request.message(),
                request.agentId(),
                request.providerId(),
                request.requestedCapabilities(),
                request.skillIds(),
                request.skillSelectionMode(),
                request.artifacts(),
                request.uploadedFiles(),
                request.inputImages(),
                request.recentMessages(),
                request.sessionSummary(),
                request.longTermMemory(),
                request.chatRouteKind(),
                snapshot,
                toolUseLimits,
                activeSkillIds,
                request.selectedDocumentIds()
        );
    }

    private List<ToolDescriptor> researchAgentTools(String userId, boolean includeWeb) {
        Set<String> names = new LinkedHashSet<>(List.of(
                "research_reflect",
                "load_skill",
                "load_skill_resource",
                "run_skill_command",
                "skill_process_status",
                "stop_skill_process"
        ));
        if (includeWeb) {
            names.add("web_search");
            names.add("web_fetch");
        }
        return agentToolService.listAvailableTools(userId).stream()
                .filter(tool -> names.contains(tool.name()))
                .toList();
    }

    private String buildResearchUnitPrompt(String researchBrief,
                                           List<String> refinementNotes,
                                           ResearchUnit unit,
                                           String evidence,
                                           List<SkillDescriptor> availableSkills) {
        String skillIndex = availableSkills.isEmpty()
                ? "- none"
                : availableSkills.stream()
                .map(this::renderSkillDescriptor)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
        return """
                You are a focused deep research unit agent.
                Gather evidence with tools when needed, keep evidence grounded, and stop when coverage is good enough or the tool budget is exhausted.
                Treat web_search as discovery and web_fetch as verification.
                Use research_reflect after you have some evidence or when coverage is thin.
                Skills are available via an index only. A listed skill is not active by default.
                Call load_skill before following any skill workflow.
                Call load_skill_resource for declared bundled references.
                Return strict JSON with keys:
                notes: string
                localConclusion: string
                sources: string[]

                Research brief:
                %s

                Refinements:
                %s

                Unit:
                title: %s
                objective: %s
                query: %s
                output focus: %s

                Available skills:
                %s

                Initial evidence:
                %s
                """.formatted(
                researchBrief,
                refinementNotes.isEmpty() ? "- none" : String.join(System.lineSeparator(), refinementNotes),
                unit.title(),
                unit.objective(),
                unit.query(),
                unit.outputFocus(),
                skillIndex,
                truncate(evidence, 9_000)
        );
    }

    private String renderSkillDescriptor(SkillDescriptor skill) {
        StringBuilder builder = new StringBuilder("- ")
                .append(skill.skillId())
                .append(" [")
                .append(skill.status())
                .append("]");
        if (skill.statusReason() != null && !skill.statusReason().isBlank()) {
            builder.append(" ").append(skill.statusReason().trim());
        }
        builder.append(": ").append(skill.description());
        if (skill.summary() != null && !skill.summary().isBlank()) {
            builder.append(" Summary: ").append(skill.summary().trim());
        }
        builder.append(" Source key: ").append(skill.sourceKey()).append(".");
        return builder.toString();
    }

    private void emitStage(AgentOutputEmitter outputEmitter, RunEventType eventType, String stage) {
        outputEmitter.emitEvent(eventType, Map.of("stage", stage));
    }

    private String renderResearchPlan(ResearchPlan researchPlan) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Research Plan\n\n");
        builder.append(researchPlan.summary()).append("\n\n");
        int index = 1;
        for (ResearchUnit unit : researchPlan.units()) {
            builder.append(index++).append(". ").append(unit.title()).append("\n")
                    .append("   - objective: ").append(unit.objective()).append("\n")
                    .append("   - query: ").append(unit.query()).append("\n")
                    .append("   - documents: ").append(unit.useDocuments()).append("\n")
                    .append("   - web: ").append(unit.useWeb()).append("\n")
                    .append("   - focus: ").append(unit.outputFocus()).append("\n");
        }
        return builder.toString().trim();
    }

    private String renderFindings(List<CompressedFinding> findings) {
        StringBuilder builder = new StringBuilder("# Compressed Findings\n\n");
        if (findings.isEmpty()) {
            return builder.append("No findings available.").toString();
        }
        for (CompressedFinding finding : findings) {
            builder.append("## ").append(finding.heading()).append("\n")
                    .append(finding.summary()).append("\n\n")
                    .append("Evidence strength: ").append(finding.evidenceStrength()).append("\n");
            if (!finding.supportingSources().isEmpty()) {
                builder.append("Sources:\n");
                for (String source : finding.supportingSources()) {
                    builder.append("- ").append(source).append("\n");
                }
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private String renderUnitOutputsForPrompt(List<ResearchUnitResult> unitResults) {
        StringBuilder builder = new StringBuilder();
        for (ResearchUnitResult unitResult : unitResults) {
            builder.append("Unit: ").append(unitResult.title()).append("\n")
                    .append("Conclusion: ").append(unitResult.localConclusion()).append("\n")
                    .append("Notes: ").append(unitResult.notes()).append("\n")
                    .append("Sources: ").append(unitResult.sources()).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String textOrFallback(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node.path(fieldName);
        if (value.isTextual() && !value.asText().isBlank()) {
            return value.asText().trim();
        }
        return fallback;
    }

    private String textOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String domainOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> readStringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                if (node.isTextual() && !node.asText().isBlank()) {
                    values.add(node.asText().trim());
                }
            }
        }
        return List.copyOf(values);
    }

    private List<String> mergeSources(List<String> currentSources, JsonNode sourceNode) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(currentSources);
        merged.addAll(readStringList(sourceNode));
        return List.copyOf(merged);
    }

    private List<ResearchSourceRecord> dedupeSourceRecords(List<ResearchSourceRecord> sourceRecords) {
        Map<String, ResearchSourceRecord> unique = new LinkedHashMap<>();
        for (ResearchSourceRecord sourceRecord : sourceRecords) {
            unique.putIfAbsent(sourceRecord.sourceId(), sourceRecord);
        }
        return List.copyOf(unique.values());
    }

    private ResearchSourceRecord documentSourceRecord(String unitId, DocumentChunk chunk) {
        return new ResearchSourceRecord(
                "document_chunk:%s".formatted(textOrFallback(chunk.chunkId(), chunk.documentId() + ":" + chunk.pageStart())),
                ResearchSourceKind.DOCUMENT_CHUNK,
                chunk.documentName(),
                null,
                "p.%d".formatted(chunk.pageStart()),
                truncate(chunk.text(), 240),
                null,
                unitId
        );
    }

    private ResearchSourceRecord webSearchSourceRecord(String unitId, String title, String url, String snippet) {
        return new ResearchSourceRecord(
                "web_result:%s".formatted(url),
                ResearchSourceKind.WEB_RESULT,
                textOrFallback(title, url),
                url,
                null,
                truncate(snippet, 240),
                domainOf(url),
                unitId
        );
    }

    private ResearchSourceRecord webPageSourceRecord(String unitId, String title, String url, String pageText) {
        return new ResearchSourceRecord(
                "web_page:%s".formatted(url),
                ResearchSourceKind.WEB_PAGE,
                textOrFallback(title, url),
                url,
                null,
                truncate(pageText, 240),
                domainOf(url),
                unitId
        );
    }

    private String renderSourceReference(ResearchSourceRecord sourceRecord) {
        if (sourceRecord.kind() == ResearchSourceKind.DOCUMENT_CHUNK) {
            return "[%s, %s]".formatted(sourceRecord.title(), sourceRecord.locator());
        }
        if (!isBlank(sourceRecord.uri())) {
            return "%s - %s".formatted(sourceRecord.title(), sourceRecord.uri());
        }
        return sourceRecord.title();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private String summarize(String response) {
        String normalized = response == null ? "" : response.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "unknown error";
        }
        return throwable.getMessage().trim();
    }

    private void logFlow(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }

    private void logModelResult(java.util.function.Supplier<String> messageSupplier) {
        if (logModelResponses && logger.isLoggable(Level.INFO)) {
            logger.info(messageSupplier);
        }
    }

    private final class CapturingEmitter implements AgentOutputEmitter {

        private final AgentOutputEmitter delegate;
        private final String unitId;
        private final String runId;
        private final List<ResearchSourceRecord> candidateSourceRecords = new ArrayList<>();
        private final List<ResearchSourceRecord> verifiedSourceRecords = new ArrayList<>();

        private CapturingEmitter(AgentOutputEmitter delegate, String unitId, String runId) {
            this.delegate = delegate;
            this.unitId = unitId;
            this.runId = runId;
        }

        @Override
        public void emitText(String delta) {
            delegate.emitText(delta);
        }

        @Override
        public void emitEvent(RunEventType eventType, Object payload) {
            if (eventType == RunEventType.EVIDENCE_CANDIDATE_ADDED) {
                captureCandidate(payload);
            } else if (eventType == RunEventType.EVIDENCE_VERIFIED_ADDED) {
                captureVerified(payload);
            }
            delegate.emitEvent(eventType, payload);
        }

        private void captureCandidate(Object payload) {
            if (!(payload instanceof Map<?, ?> map)) {
                return;
            }
            String title = map.get("title") instanceof String value ? value.trim() : "";
            String url = map.get("url") instanceof String value ? value.trim() : "";
            if (title.isBlank() || url.isBlank()) {
                return;
            }
            candidateSourceRecords.add(webSearchSourceRecord(unitId, title, url, ""));
        }

        private void captureVerified(Object payload) {
            if (!(payload instanceof Map<?, ?> map)) {
                return;
            }
            String title = map.get("title") instanceof String value ? value.trim() : "";
            String url = map.get("url") instanceof String value ? value.trim() : "";
            if (title.isBlank() || url.isBlank()) {
                return;
            }
            verifiedSourceRecords.add(webPageSourceRecord(unitId, title, url, ""));
        }

        private List<ResearchSourceRecord> candidateSourceRecords() {
            return List.copyOf(candidateSourceRecords);
        }

        private List<ResearchSourceRecord> verifiedSourceRecords() {
            return List.copyOf(verifiedSourceRecords);
        }

        private List<String> candidateSources() {
            return candidateSourceRecords.stream()
                    .map(DefaultResearchExecutionSupport.this::renderSourceReference)
                    .toList();
        }

        private List<String> verifiedSources() {
            return verifiedSourceRecords.stream()
                    .map(DefaultResearchExecutionSupport.this::renderSourceReference)
                    .toList();
        }
    }

}
