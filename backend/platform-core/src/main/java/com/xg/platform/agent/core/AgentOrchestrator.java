package com.xg.platform.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.agent.AgentCapability;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.contracts.artifact.ArtifactVisibility;
import com.xg.platform.contracts.artifact.RegisterArtifactCommand;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.message.ApprovedResearchPlan;
import com.xg.platform.contracts.message.ResearchPlanStep;
import com.xg.platform.contracts.message.RunEventType;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import com.xg.platform.agent.core.chat.ChatRouterService;
import com.xg.platform.agent.core.research.execution.ResearchExecutionSupport;
import com.xg.platform.agent.core.research.execution.ResearchEvidenceSupport;
import com.xg.platform.memory.ChunkIndexStore;
import com.xg.platform.memory.ContextAssembler;
import com.xg.platform.memory.DocumentChunk;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.memory.RetrievedChunk;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.tools.SkillDefinition;
import com.xg.platform.tools.SkillRuntimeSnapshot;
import com.xg.platform.tools.SkillRegistry;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolExecutionRequest;
import com.xg.platform.tools.ToolExecutionResult;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;
import com.xg.platform.contracts.workspace.WorkspaceArea;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import java.util.function.Supplier;

public class AgentOrchestrator {

    private static final Logger logger = Logger.getLogger(AgentOrchestrator.class.getName());
    private static final Pattern CHINESE_LOCATION_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z\\s]{2,30}?)(?:市|区|县|省)?(?:今天|明天|后天|天气|未来\\d+天)");
    private static final Pattern ENGLISH_LOCATION_PATTERN = Pattern.compile("(?:weather|forecast)(?:\\s+in|\\s+for)?\\s+([A-Za-z][A-Za-z\\s,.-]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final List<String> CHAT_LOOKUP_TERMS = List.of(
            "weather",
            "forecast",
            "temperature",
            "rain",
            "news",
            "headline",
            "today",
            "tomorrow",
            "search",
            "look up",
            "lookup",
            "browse",
            "web",
            "internet",
            "网址",
            "链接",
            "网站",
            "网页",
            "搜索",
            "查一下",
            "查下",
            "查一查",
            "查询",
            "帮我查",
            "天气",
            "气温",
            "温度",
            "降雨",
            "下雨",
            "明天",
            "今天",
            "最新",
            "新闻"
    );

    private static final Pattern WEATHER_CHINESE_LOCATION_PATTERN = Pattern.compile(
            "([\\p{IsHan}A-Za-z\\s]{2,30}?)(?:\\u5e02|\\u533a|\\u53bf|\\u7701)?(?:\\u4eca\\u5929|\\u660e\\u5929|\\u540e\\u5929|\\u5929\\u6c14|\\u672a\\u6765\\s*\\d+\\s*\\u5929)"
    );

    private final AgentPromptService agentPromptService;
    private final SkillRegistry skillRegistry;
    private final AgentToolService agentToolService;
    private final ChatRouterService chatRouterService;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final DocumentStore documentStore;
    private final DocumentIngestService documentIngestService;
    private final ChunkIndexStore chunkIndexStore;
    private final ContextAssembler contextAssembler;
    private final ArtifactService artifactService;
    private final WorkspaceManager workspaceManager;
    private final ThreadRuntimeService threadRuntimeService;
    private final ResearchExecutionSupport researchExecutionSupport;
    private final ObjectMapper objectMapper;
    private final boolean logPrompts;
    private final boolean logAgentFlow;
    private final boolean logModelResponses;
    private final int chatMaxToolCalls;
    private final int chatMaxSearchCalls;
    private final int chatMaxFetchCalls;
    private final int chatMinVerifiedSources;
    private final long chatTimeoutMs;

    public AgentOrchestrator(AgentPromptService agentPromptService,
                             SkillRegistry skillRegistry,
                             AgentToolService agentToolService,
                             ChatRouterService chatRouterService,
                             AgentTurnExecutionSupport agentTurnExecutionSupport,
                             DocumentStore documentStore,
                             DocumentIngestService documentIngestService,
                             ChunkIndexStore chunkIndexStore,
                             ContextAssembler contextAssembler,
                             ArtifactService artifactService,
                             WorkspaceManager workspaceManager,
                             ThreadRuntimeService threadRuntimeService,
                             ResearchExecutionSupport researchExecutionSupport,
                             ObjectMapper objectMapper,
                             boolean logPrompts,
                             boolean logAgentFlow,
                             boolean logModelResponses,
                             int chatMaxToolCalls,
                             int chatMaxSearchCalls,
                             int chatMaxFetchCalls,
                             int chatMinVerifiedSources,
                             long chatTimeoutMs) {
        this.agentPromptService = agentPromptService;
        this.skillRegistry = skillRegistry;
        this.agentToolService = agentToolService;
        this.chatRouterService = chatRouterService;
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.documentStore = documentStore;
        this.documentIngestService = documentIngestService;
        this.chunkIndexStore = chunkIndexStore;
        this.contextAssembler = contextAssembler;
        this.artifactService = artifactService;
        this.workspaceManager = workspaceManager;
        this.threadRuntimeService = threadRuntimeService;
        this.researchExecutionSupport = researchExecutionSupport;
        this.objectMapper = objectMapper;
        this.logPrompts = logPrompts;
        this.logAgentFlow = logAgentFlow;
        this.logModelResponses = logModelResponses;
        this.chatMaxToolCalls = chatMaxToolCalls;
        this.chatMaxSearchCalls = chatMaxSearchCalls;
        this.chatMaxFetchCalls = chatMaxFetchCalls;
        this.chatMinVerifiedSources = chatMinVerifiedSources;
        this.chatTimeoutMs = chatTimeoutMs;
    }

    public AgentExecutionResult execute(AgentMode mode, AgentExecutionRequest request, AgentOutputEmitter outputEmitter) {
        SkillRuntimeSnapshot snapshot = snapshotFor(request);
        AgentExecutionRequest runtimeRequest = withRuntimeContext(request, snapshot, request.toolUseLimits());
        logFlow(() -> "orchestrator.execute mode=" + mode
                + " thread=" + runtimeRequest.threadId()
                + " run=" + runtimeRequest.runId()
                + " recentMessages=" + runtimeRequest.recentMessages().size());
        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "prepare");
        List<DocumentRecord> documents = documentStore.listDocuments(runtimeRequest.userId(), runtimeRequest.threadId());
        List<SkillDescriptor> availableSkills = skillRegistry.listDescriptors(runtimeRequest.userId());
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "prepare");

        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "clarify");
        String clarification = clarificationQuestion(runtimeRequest, documents);
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "clarify");
        if (clarification != null) {
            for (String segment : split(clarification, 180)) {
                outputEmitter.emitText(segment);
            }
            String providerId = agentTurnExecutionSupport.resolveProviderId(runtimeRequest.userId(), runtimeRequest.providerId());
            return new AgentExecutionResult(
                    agentId(mode),
                    providerId,
                    capability(mode),
                    summarize(clarification),
                    clarification,
                    false,
                    runtimeRequest.chatRouteKind() == ChatRouteKind.DOCUMENT_QA ? "document-qa" : "chat",
                    runtimeRequest.chatRouteKind() == null ? ChatRouteKind.CHAT : runtimeRequest.chatRouteKind(),
                    false,
                    List.of(),
                    0
            );
        }

        boolean docsWorkflow = shouldUseDocsWorkflow(mode, runtimeRequest, documents);
        ChatRouteKind routeKind = runtimeRequest.chatRouteKind();
        logFlow(() -> "orchestrator path mode=" + mode
                + " docsWorkflow=" + docsWorkflow
                + " documents=" + documents.size()
                + " route=" + routeKind);
        String providerId = agentTurnExecutionSupport.resolveProviderId(runtimeRequest.userId(), runtimeRequest.providerId());
        if (routeKind == ChatRouteKind.DOCUMENT_QA) {
            return executeDocs(mode, runtimeRequest, outputEmitter, documents, availableSkills, providerId);
        }
        if (!docsWorkflow) {
            return executeChat(mode, runtimeRequest, outputEmitter, documents, availableSkills, providerId);
        }
        return executeDocs(mode, runtimeRequest, outputEmitter, documents, availableSkills, providerId);
    }

    public AgentExecutionResult executeResearch(AgentExecutionRequest request,
                                                ApprovedResearchPlan approvedPlan,
                                                Supplier<List<String>> refinementNotesSupplier,
                                                AgentOutputEmitter outputEmitter) {
        SkillRuntimeSnapshot snapshot = snapshotFor(request);
        AgentExecutionRequest runtimeRequest = withRuntimeContext(request, snapshot, request.toolUseLimits());
        logFlow(() -> "executeResearch thread=" + runtimeRequest.threadId()
                + " run=" + runtimeRequest.runId()
                + " brief=" + summarize(approvedPlan.brief()));
        List<DocumentRecord> documents = prepareResearchExecution(runtimeRequest, outputEmitter);
        String providerId = agentTurnExecutionSupport.resolveProviderId(runtimeRequest.userId(), runtimeRequest.providerId());
        List<String> refinementNotes = refinementNotesSupplier.get();
        ResearchPlan researchPlan = createResearchPlan(approvedPlan, documents);
        logFlow(() -> "researchPlan created thread=" + runtimeRequest.threadId()
                + " run=" + runtimeRequest.runId()
                + " units=" + researchPlan.units().size()
                + " summary=" + summarize(researchPlan.summary()));
        ArtifactRecord planArtifact = writeNoteArtifact(
                runtimeRequest,
                outputEmitter,
                runtimeRequest.runId() + "/research/research_plan.md",
                "research_plan.md",
                renderResearchPlan(researchPlan)
        );
        outputEmitter.emitEvent(RunEventType.RESEARCH_PLAN_CREATED, Map.of(
                "runId", runtimeRequest.runId(),
                "artifactId", planArtifact.artifactId(),
                "summary", researchPlan.summary(),
                "units", researchPlan.units().size()
        ));

        List<ResearchUnitResult> unitResults = new ArrayList<>();
        int stepIndex = 0;
        for (ResearchUnit unit : researchPlan.units()) {
            stepIndex++;
            List<String> unitRefinements = refinementNotesSupplier.get();
            outputEmitter.emitEvent(RunEventType.RESEARCH_STEP_STARTED, Map.of(
                    "runId", runtimeRequest.runId(),
                    "stepId", unit.unitId(),
                    "title", unit.title(),
                    "stepIndex", stepIndex,
                    "totalSteps", researchPlan.units().size()
            ));
            outputEmitter.emitEvent(RunEventType.RESEARCH_ACTIVITY, Map.of(
                    "runId", runtimeRequest.runId(),
                    "stepId", unit.unitId(),
                    "summary", "Starting research step: " + unit.title(),
                    "kind", "step-started"
            ));
            outputEmitter.emitEvent(RunEventType.RESEARCH_UNIT_STARTED, Map.of(
                    "runId", runtimeRequest.runId(),
                    "unitId", unit.unitId(),
                    "title", unit.title()
            ));
            ResearchUnitResult unitResult = executeResearchUnit(providerId, runtimeRequest, approvedPlan.brief(), unitRefinements, documents, unit, outputEmitter, stepIndex, researchPlan.units().size());
            logFlow(() -> "researchUnit completed run=" + runtimeRequest.runId()
                    + " unit=" + unit.unitId()
                    + " title=" + unit.title()
                    + " sources=" + unitResult.sources().size());
            unitResults.add(unitResult);
            writeNoteArtifact(
                    runtimeRequest,
                    outputEmitter,
                    runtimeRequest.runId() + "/research/" + unit.unitId() + ".md",
                    unit.unitId() + ".md",
                    renderResearchUnitResult(unitResult)
            );
            outputEmitter.emitEvent(RunEventType.RESEARCH_UNIT_COMPLETED, Map.of(
                    "runId", runtimeRequest.runId(),
                    "unitId", unit.unitId(),
                    "title", unit.title(),
                    "sources", unitResult.sources().size()
            ));
            outputEmitter.emitEvent(RunEventType.RESEARCH_STEP_COMPLETED, Map.of(
                    "runId", runtimeRequest.runId(),
                    "stepId", unit.unitId(),
                    "title", unit.title(),
                    "stepIndex", stepIndex,
                    "totalSteps", researchPlan.units().size(),
                    "sources", unitResult.sources().size()
            ));
            outputEmitter.emitEvent(RunEventType.RESEARCH_ACTIVITY, Map.of(
                    "runId", runtimeRequest.runId(),
                    "stepId", unit.unitId(),
                    "summary", "Completed step '" + unit.title() + "' with " + unitResult.sources().size() + " source references.",
                    "kind", "step-completed"
            ));
        }

        List<CompressedFinding> findings = compressFindings(providerId, runtimeRequest, approvedPlan.brief(), researchPlan, unitResults);
        logFlow(() -> "findings compressed run=" + runtimeRequest.runId() + " count=" + findings.size());
        writeNoteArtifact(
                runtimeRequest,
                outputEmitter,
                runtimeRequest.runId() + "/research/findings.md",
                "findings.md",
                renderFindings(findings)
        );
        outputEmitter.emitEvent(RunEventType.RESEARCH_FINDINGS_COMPRESSED, Map.of(
                "runId", runtimeRequest.runId(),
                "findings", findings.size()
        ));

        List<ReportCitation> citations = ResearchEvidenceSupport.buildReportCitations(unitResults);
        String finalReport = ResearchEvidenceSupport.ensureReportCitations(
                generateFinalReport(providerId, runtimeRequest, approvedPlan.brief(), researchPlan, findings, citations, refinementNotesSupplier.get()),
                citations
        );
        List<ReportCitation> usedCitations = ResearchEvidenceSupport.markCitationUsage(citations, finalReport);
        logFlow(() -> "final report created run=" + runtimeRequest.runId() + " summary=" + summarize(finalReport));
        outputEmitter.emitEvent(RunEventType.RESEARCH_REPORT_CREATED, Map.of(
                "runId", runtimeRequest.runId(),
                "summary", summarize(finalReport),
                "citations", usedCitations.stream().filter(ReportCitation::usedInReport).count()
        ));
        outputEmitter.emitEvent(RunEventType.RESEARCH_REPORT_READY, Map.of(
                "runId", runtimeRequest.runId(),
                "summary", summarize(finalReport),
                "citations", usedCitations.stream().filter(ReportCitation::usedInReport).count(),
                "sources", citations.size()
        ));
        for (String segment : split(finalReport, 180)) {
            outputEmitter.emitText(segment);
        }
        return new AgentExecutionResult(
                "docs-agent",
                providerId,
                AgentCapability.DOCS,
                summarize(finalReport),
                finalReport,
                true,
                "research-execution",
                ChatRouteKind.RESEARCH_DRAFT,
                true,
                List.of(),
                0
        );
    }

    public List<DocumentRecord> prepareResearchExecution(AgentExecutionRequest request,
                                                         AgentOutputEmitter outputEmitter) {
        return researchExecutionSupport.prepareResearchExecution(request, outputEmitter);
    }

    private AgentExecutionResult executeDocs(AgentMode mode,
                                             AgentExecutionRequest request,
                                             AgentOutputEmitter outputEmitter,
                                             List<DocumentRecord> documents,
                                             List<SkillDescriptor> availableSkills,
                                             String providerId) {
        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "ensure-ingest");
        documents = documentIngestService.ensureReadyDocuments(request.userId(), request.threadId(), documents, request.runId(), outputEmitter);
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "ensure-ingest");
        List<ToolDescriptor> docsTools = availableChatTools(request.userId());

        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "plan");
        String planText = agentTurnExecutionSupport.runTextTurn(
                request.userId(),
                providerId,
                buildPrompt(
                        mode,
                        request,
                        ChatRouteKind.DOCUMENT_QA,
                        documents,
                        List.of(),
                        "PLAN",
                        List.of("prepare", "ensure-ingest"),
                        "",
                        availableSkills,
                        docsTools
                ),
                "Create a concise research plan for this request. Focus on evidence gathering, synthesis, and limits.\n\nUser request:\n" + request.message()
        );
        ArtifactRecord planArtifact = writeNoteArtifact(request, outputEmitter, request.runId() + "/research/research_plan.md", "research_plan.md", planText);
        outputEmitter.emitEvent(RunEventType.PLAN_CREATED, Map.of(
                "runId", request.runId(),
                "artifactId", planArtifact.artifactId(),
                "summary", summarize(planText)
        ));
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "plan");

        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "explore");
        outputEmitter.emitEvent(RunEventType.EXPLORE_STARTED, Map.of("runId", request.runId()));
        ExplorationResult explorationResult = exploreDocuments(request, documents, planText, outputEmitter);
        writeResearchArtifacts(request, outputEmitter, explorationResult);
        outputEmitter.emitEvent(RunEventType.EXPLORE_COMPLETED, Map.of(
                "runId", request.runId(),
                "chunks", explorationResult.retrievedChunks().size(),
                "actions", explorationResult.actions()
        ));
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "explore");

        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "synthesize");
        String draft = agentTurnExecutionSupport.runTextTurn(
                request.userId(),
                providerId,
                buildPrompt(
                        mode,
                        request,
                        ChatRouteKind.DOCUMENT_QA,
                        documents,
                        explorationResult.retrievedChunks(),
                        "SYNTHESIZE",
                        explorationResult.actions(),
                        explorationResult.workingMemory(),
                        availableSkills,
                        docsTools
                ),
                "Draft the final answer for the user based on the current evidence.\n\nUser request:\n" + request.message()
        );
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "synthesize");

        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "verify");
        outputEmitter.emitEvent(RunEventType.VERIFY_STARTED, Map.of("runId", request.runId()));
        String verified = agentTurnExecutionSupport.runTextTurn(
                request.userId(),
                providerId,
                buildPrompt(
                        mode,
                        request,
                        ChatRouteKind.DOCUMENT_QA,
                        documents,
                        explorationResult.retrievedChunks(),
                        "VERIFY",
                        explorationResult.actions(),
                        explorationResult.workingMemory(),
                        availableSkills,
                        docsTools
                ),
                """
                        Review the draft answer for evidence grounding and clarity.
                        Improve it if needed. Keep citations inline when evidence exists.

                        User request:
                        %s

                        Draft:
                        %s
                        """.formatted(request.message(), draft)
        );
        outputEmitter.emitEvent(RunEventType.VERIFY_COMPLETED, Map.of("runId", request.runId()));
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "verify");

        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "deliver");
        String finalContent = enforceCitations(verified, explorationResult.retrievedChunks(), !documents.isEmpty());
        for (String segment : split(finalContent, 180)) {
            outputEmitter.emitText(segment);
        }
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "deliver");
        return new AgentExecutionResult(
                agentId(mode),
                providerId,
                capability(mode),
                summarize(finalContent),
                finalContent,
                true,
                "document-qa",
                ChatRouteKind.DOCUMENT_QA,
                false,
                List.of(),
                0
        );
    }

    public ResearchPlan createResearchPlan(ApprovedResearchPlan approvedPlan,
                                           List<DocumentRecord> documents) {
        return researchExecutionSupport.createResearchPlan(approvedPlan, documents);
    }

    public ResearchUnitResult executeResearchUnit(String providerId,
                                                  AgentExecutionRequest request,
                                                  String researchBrief,
                                                  List<String> refinementNotes,
                                                  List<DocumentRecord> documents,
                                                  ResearchUnit unit,
                                                  AgentOutputEmitter outputEmitter,
                                                  int stepIndex,
                                                  int totalSteps) {
        return researchExecutionSupport.executeResearchUnit(
                providerId,
                request,
                researchBrief,
                refinementNotes,
                documents,
                unit,
                outputEmitter,
                stepIndex,
                totalSteps
        );
    }

    public List<CompressedFinding> compressFindings(String providerId,
                                                    AgentExecutionRequest request,
                                                    String researchBrief,
                                                    ResearchPlan researchPlan,
                                                    List<ResearchUnitResult> unitResults) {
        return researchExecutionSupport.compressFindings(providerId, request, researchBrief, researchPlan, unitResults);
    }

    public String generateFinalReport(String providerId,
                                      AgentExecutionRequest request,
                                      String researchBrief,
                                      ResearchPlan researchPlan,
                                      List<CompressedFinding> findings,
                                      List<ReportCitation> citations,
                                      List<String> refinementNotes) {
        return researchExecutionSupport.generateFinalReport(
                providerId,
                request,
                researchBrief,
                researchPlan,
                findings,
                citations,
                refinementNotes
        );
    }

    private AgentExecutionResult executeChat(AgentMode mode,
                                             AgentExecutionRequest request,
                                             AgentOutputEmitter outputEmitter,
                                             List<DocumentRecord> documents,
                                             List<SkillDescriptor> availableSkills,
                                             String providerId) {
        ChatRouteKind effectiveRoute = normalizeChatRoute(request.chatRouteKind());
        List<ToolDescriptor> availableTools = availableChatTools(request.userId());
        String prompt = buildPrompt(
                mode,
                request,
                effectiveRoute,
                documents,
                List.of(),
                "SYNTHESIZE",
                List.of("prepare"),
                "",
                availableSkills,
                availableTools
        );
        String response = agentTurnExecutionSupport.runModelLoop(providerId, request, prompt, availableTools, outputEmitter);
        String finalContent = enforceCitations(response, List.of(), !documents.isEmpty());
        return new AgentExecutionResult(
                agentId(mode),
                providerId,
                capability(mode),
                summarize(finalContent),
                finalContent,
                true,
                "chat",
                effectiveRoute,
                !availableTools.isEmpty(),
                List.of(),
                0
        );
    }

    private AgentExecutionResult executeRealtimeLookup(AgentMode mode,
                                                       AgentExecutionRequest request,
                                                       AgentOutputEmitter outputEmitter,
                                                       List<DocumentRecord> documents,
                                                       List<SkillDescriptor> availableSkills,
                                                       String providerId) {
        WeatherIntent weatherIntent = parseStableWeatherIntent(request.message());
        if (weatherIntent.isWeather() && weatherIntent.location().isBlank()) {
            String clarification = containsChinese(request.message())
                    ? "请告诉我要查询天气的城市或地区，例如“天津明天天气”。"
                    : "Please tell me which city or region you want the weather for.";
            return new AgentExecutionResult(
                    agentId(mode),
                    providerId,
                    capability(mode),
                    summarize(clarification),
                    clarification,
                    false,
                    "chat",
                    ChatRouteKind.CHAT,
                    true,
                    List.of(),
                    0
            );
        }
        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "explore");
        LookupResult lookupResult = collectRealtimeLookupEvidence(request, outputEmitter, weatherIntent);
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "explore");

        emitStage(outputEmitter, RunEventType.STAGE_STARTED, "synthesize");
        String finalContent;
        if (weatherIntent.isWeather()) {
            finalContent = lookupResult.weatherPayload() != null
                    ? renderStableWeatherAnswer(request.message(), lookupResult.weatherPayload()).trim()
                    : renderWeatherUnavailableMessage(request.message(), weatherIntent.location(), lookupResult.weatherError()).trim();
        } else {
            String prompt = buildPrompt(
                    mode,
                    request,
                    ChatRouteKind.CHAT,
                    documents,
                    List.of(),
                    "SYNTHESIZE",
                    lookupResult.actions(),
                    lookupResult.workingMemory(),
                    availableSkills,
                    List.of()
            );
            finalContent = agentTurnExecutionSupport.runTextTurn(
                    request.userId(),
                    providerId,
                    prompt,
                    buildLookupSynthesisPrompt(request.message(), lookupResult)
            ).trim();
        }
        emitStage(outputEmitter, RunEventType.STAGE_COMPLETED, "synthesize");
        if (lookupResult.verifiedSources().isEmpty() && !lookupResult.candidateSources().isEmpty()) {
            finalContent = finalContent + System.lineSeparator() + System.lineSeparator()
                    + "Note: this answer is based on search candidates and could not be fully verified by fetching the target pages.";
        }
        return new AgentExecutionResult(
                agentId(mode),
                providerId,
                capability(mode),
                summarize(finalContent),
                finalContent,
                true,
                "chat",
                ChatRouteKind.CHAT,
                true,
                lookupResult.verifiedSources(),
                lookupResult.verifiedSources().size()
        );
    }

    private LookupResult collectRealtimeLookupEvidence(AgentExecutionRequest request,
                                                       AgentOutputEmitter outputEmitter,
                                                       WeatherIntent weatherIntent) {
        List<String> actions = new ArrayList<>();
        List<ExecutionSource> candidateSources = new ArrayList<>();
        List<ExecutionSource> verifiedSources = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode weatherPayload = null;
        if (weatherIntent.isWeather() && !weatherIntent.location().isBlank()) {
            try {
                ToolDescriptor weatherTool = agentToolService.requireTool(request.userId(), "weather");
                actions.add("weather");
                ToolExecutionResult weatherResult = executeTool(
                        request,
                        weatherTool,
                        objectMapper.createObjectNode()
                                .put("location", weatherIntent.location())
                                .put("dayOffset", weatherIntent.dayOffset())
                                .put("days", weatherIntent.days()),
                        outputEmitter
                );
                weatherPayload = weatherResult.output();
                ExecutionSource source = weatherExecutionSource(weatherPayload);
                if (source != null) {
                    verifiedSources.add(source);
                    outputEmitter.emitEvent(RunEventType.EVIDENCE_VERIFIED_ADDED, Map.of(
                            "kind", source.kind(),
                            "title", source.title(),
                            "domain", source.domain(),
                            "url", source.url()
                    ));
                }
                String workingMemory = verifiedSources.isEmpty()
                        ? "Weather lookup ran but did not produce a usable forecast."
                        : "Collected verified structured weather data for " + weatherIntent.location() + ".";
                return new LookupResult(List.of(), List.copyOf(verifiedSources), List.copyOf(actions), workingMemory, weatherPayload, null);
            } catch (RuntimeException exception) {
                String weatherError = exception.getMessage() == null || exception.getMessage().isBlank()
                        ? "weather lookup failed"
                        : exception.getMessage().trim();
                String workingMemory = "Weather lookup failed for " + weatherIntent.location() + ": " + weatherError;
                return new LookupResult(List.of(), List.of(), List.copyOf(actions), workingMemory, null, weatherError);
            }
        }
        ToolDescriptor webSearch = agentToolService.requireTool(request.userId(), "web_search");
        ToolDescriptor webFetch = agentToolService.requireTool(request.userId(), "web_fetch");
        ToolExecutionResult searchResult = executeTool(
                request,
                webSearch,
                objectMapper.createObjectNode()
                        .put("query", request.message())
                        .put("maxResults", 5),
                outputEmitter
        );
        actions.add("web search");
        for (var resultNode : searchResult.output().path("results")) {
            String title = resultNode.path("title").asText("").trim();
            String url = resultNode.path("url").asText("").trim();
            if (title.isBlank() || url.isBlank()) {
                continue;
            }
            ExecutionSource source = new ExecutionSource(
                    "WEB_RESULT",
                    title,
                    domainOf(url),
                    url,
                    false,
                    false
            );
            candidateSources.add(source);
            outputEmitter.emitEvent(RunEventType.EVIDENCE_CANDIDATE_ADDED, Map.of(
                    "kind", source.kind(),
                    "title", source.title(),
                    "domain", source.domain(),
                    "url", source.url()
            ));
        }
        List<ExecutionSource> topCandidates = candidateSources.stream()
                .collect(java.util.stream.Collectors.toMap(ExecutionSource::url, source -> source, (left, right) -> left, java.util.LinkedHashMap::new))
                .values()
                .stream()
                .limit(3)
                .toList();
        for (ExecutionSource candidate : topCandidates) {
            try {
                ToolExecutionResult fetchResult = executeTool(
                        request,
                        webFetch,
                        objectMapper.createObjectNode().put("url", candidate.url()),
                        outputEmitter
                );
                String fetchedText = fetchResult.output().path("text").asText("").trim();
                if (fetchedText.isBlank()) {
                    continue;
                }
                ExecutionSource verified = new ExecutionSource(
                        "WEB_PAGE",
                        candidate.title(),
                        candidate.domain(),
                        candidate.url(),
                        true,
                        true
                );
                verifiedSources.add(verified);
                outputEmitter.emitEvent(RunEventType.EVIDENCE_VERIFIED_ADDED, Map.of(
                        "kind", verified.kind(),
                        "title", verified.title(),
                        "domain", verified.domain(),
                        "url", verified.url()
                ));
                actions.add("web fetch");
            } catch (RuntimeException ignored) {
                // Tool failures are already emitted as run events.
            }
        }
        List<ExecutionSource> uniqueVerified = verifiedSources.stream()
                .collect(java.util.stream.Collectors.toMap(ExecutionSource::url, source -> source, (left, right) -> left, java.util.LinkedHashMap::new))
                .values()
                .stream()
                .toList();
        String workingMemory = uniqueVerified.isEmpty()
                ? "Collected search candidates but could not fully verify target pages."
                : "Collected " + uniqueVerified.size() + " verified web pages for synthesis.";
        return new LookupResult(List.copyOf(candidateSources), uniqueVerified, List.copyOf(actions), workingMemory, null, null);
    }

    private ToolExecutionResult executeTool(AgentExecutionRequest request,
                                            ToolDescriptor tool,
                                            com.fasterxml.jackson.databind.JsonNode arguments,
                                            AgentOutputEmitter outputEmitter) {
        outputEmitter.emitEvent(RunEventType.TOOL_STARTED, Map.of("toolName", tool.name()));
        try {
            ToolExecutionResult result = ToolExecutionGuard.execute(
                    tool.name(),
                    request.toolUseLimits() == null ? 30_000L : request.toolUseLimits().timeoutMs(),
                    () -> agentToolService.execute(new ToolExecutionRequest(
                            request.userId(),
                            request.threadId(),
                            request.runId(),
                            tool,
                            arguments,
                            request.skillRuntimeSnapshot(),
                            request.activeSkillIds()
                    ))
            );
            outputEmitter.emitEvent(RunEventType.TOOL_COMPLETED, Map.of("toolName", tool.name()));
            return result;
        } catch (RuntimeException exception) {
            outputEmitter.emitEvent(RunEventType.TOOL_FAILED, Map.of(
                    "toolName", tool.name(),
                    "error", exception.getMessage() == null ? "Tool execution failed" : exception.getMessage()
            ));
            throw exception;
        }
    }

    private String buildLookupSynthesisPrompt(String userMessage, LookupResult lookupResult) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                Answer the user's realtime lookup request using the evidence below.
                Rules:
                - Prefer verified fetched pages over search-result snippets.
                - If no verified pages are available, say the answer is not fully verified.
                - Do not describe internal planning or tool usage.
                - Keep the answer concise and practical.

                User request:
                """).append(userMessage).append("\n\n");
        if (!lookupResult.verifiedSources().isEmpty()) {
            builder.append("Verified sources:\n");
            for (ExecutionSource source : lookupResult.verifiedSources()) {
                builder.append("- ")
                        .append(source.title())
                        .append(" | ")
                        .append(source.domain())
                        .append(" | ")
                        .append(source.url())
                        .append("\n");
            }
        } else {
            builder.append("Search candidates (not fully verified):\n");
            for (ExecutionSource source : lookupResult.candidateSources().stream().limit(3).toList()) {
                builder.append("- ")
                        .append(source.title())
                        .append(" | ")
                        .append(source.domain())
                        .append(" | ")
                        .append(source.url())
                        .append("\n");
            }
        }
        return builder.toString().trim();
    }

    private ExecutionSource weatherExecutionSource(com.fasterxml.jackson.databind.JsonNode weatherPayload) {
        if (weatherPayload == null) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode source = weatherPayload.path("source");
        String url = source.path("url").asText("").trim();
        String title = source.path("title").asText("").trim();
        if (url.isBlank() || title.isBlank()) {
            return null;
        }
        return new ExecutionSource(
                "WEATHER_REPORT",
                title,
                source.path("domain").asText("api.open-meteo.com").trim(),
                url,
                true,
                true
        );
    }

    private String renderStableWeatherAnswer(String userMessage, com.fasterxml.jackson.databind.JsonNode weatherPayload) {
        String location = weatherPayload.path("location").asText("the requested location");
        int dayOffset = weatherPayload.path("dayOffset").asInt(0);
        String label = switch (dayOffset) {
            case 1 -> containsChinese(userMessage) ? "\u660e\u5929" : "tomorrow";
            case 2 -> containsChinese(userMessage) ? "\u540e\u5929" : "the day after tomorrow";
            default -> containsChinese(userMessage) ? "\u4eca\u5929" : "today";
        };
        String condition = weatherPayload.path("condition").asText("");
        String min = weatherPayload.path("temperatureMin").asText("");
        String max = weatherPayload.path("temperatureMax").asText("");
        String wind = weatherPayload.path("wind").asText("");
        String precipitation = weatherPayload.path("precipitation").asText("");
        String sourceLabel = weatherSourceLabel(weatherPayload);
        if (containsChinese(userMessage)) {
            StringBuilder builder = new StringBuilder();
            builder.append(location).append(label).append("\u7684\u5929\u6c14\uff1a").append(System.lineSeparator())
                    .append("- \u5929\u6c14\uff1a").append(condition.isBlank() ? "\u6682\u65e0\u6570\u636e" : condition).append(System.lineSeparator())
                    .append("- \u6c14\u6e29\uff1a").append(min).append("\u00b0C ~ ").append(max).append("\u00b0C").append(System.lineSeparator());
            if (!wind.isBlank()) {
                builder.append("- \u98ce\u529b\uff1a").append(wind).append(System.lineSeparator());
            }
            if (!precipitation.isBlank()) {
                builder.append("- \u964d\u6c34\uff1a").append(precipitation).append(System.lineSeparator());
            }
            builder.append(System.lineSeparator()).append("\u6765\u6e90\uff1a").append(sourceLabel);
            return builder.toString().trim();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Weather for ").append(location).append(" ").append(label).append(":").append(System.lineSeparator())
                .append("- Condition: ").append(condition.isBlank() ? "n/a" : condition).append(System.lineSeparator())
                .append("- Temperature: ").append(min).append("\u00b0C to ").append(max).append("\u00b0C").append(System.lineSeparator());
        if (!wind.isBlank()) {
            builder.append("- Wind: ").append(wind).append(System.lineSeparator());
        }
        if (!precipitation.isBlank()) {
            builder.append("- Precipitation: ").append(precipitation).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("Source: ").append(sourceLabel);
        return builder.toString().trim();
    }

    private String renderWeatherUnavailableAnswerLegacy(String userMessage, String location, String weatherError) {
        String place = location == null || location.isBlank()
                ? containsChinese(userMessage) ? "该地区" : "the requested location"
                : location;
        boolean missingLocation = weatherError != null && weatherError.contains("no matching location");
        if (containsChinese(userMessage)) {
            return missingLocation
                    ? "我暂时没找到“" + place + "”对应的天气地点，请换成更具体的城市或地区再试一次。"
                    : "我暂时没能获取“" + place + "”的天气数据，请稍后再试。";
        }
        return missingLocation
                ? "I couldn't match \"" + place + "\" to a weather location. Please try a more specific city or region."
                : "I couldn't fetch weather data for \"" + place + "\" just now. Please try again in a moment.";
    }

    private String renderWeatherUnavailableMessage(String userMessage, String location, String weatherError) {
        String place = location == null || location.isBlank()
                ? containsChinese(userMessage) ? "\u8be5\u5730\u533a" : "the requested location"
                : location;
        boolean missingLocation = weatherError != null && weatherError.contains("no matching location");
        if (containsChinese(userMessage)) {
            return missingLocation
                    ? "\u6211\u6682\u65f6\u6ca1\u627e\u5230\u201c" + place + "\u201d\u5bf9\u5e94\u7684\u5929\u6c14\u5730\u70b9\uff0c\u8bf7\u6362\u6210\u66f4\u5177\u4f53\u7684\u57ce\u5e02\u6216\u5730\u533a\u518d\u8bd5\u4e00\u6b21\u3002"
                    : "\u6211\u6682\u65f6\u6ca1\u80fd\u83b7\u53d6\u201c" + place + "\u201d\u7684\u5929\u6c14\u6570\u636e\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5\u3002";
        }
        return missingLocation
                ? "I couldn't match \"" + place + "\" to a weather location. Please try a more specific city or region."
                : "I couldn't fetch weather data for \"" + place + "\" just now. Please try again in a moment.";
    }

    private String weatherSourceLabel(com.fasterxml.jackson.databind.JsonNode weatherPayload) {
        String provider = weatherPayload.path("source").path("provider").asText("").trim();
        if (!provider.isBlank()) {
            return provider;
        }
        String domain = weatherPayload.path("source").path("domain").asText("").trim();
        return domain.isBlank() ? "weather provider" : domain;
    }

    private WeatherIntent parseStableWeatherIntent(String message) {
        if (message == null || message.isBlank()) {
            return new WeatherIntent(false, "", 0, 1);
        }
        String normalized = message.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean isWeather = containsAny(lower,
                "weather", "forecast", "temperature", "rain",
                "\u5929\u6c14", "\u9884\u62a5", "\u6c14\u6e29", "\u6e29\u5ea6", "\u964d\u96e8", "\u964d\u6c34");
        if (!isWeather) {
            return new WeatherIntent(false, "", 0, 1);
        }
        int dayOffset = containsAny(lower, "\u540e\u5929", "the day after tomorrow") ? 2
                : containsAny(lower, "tomorrow", "\u660e\u5929") ? 1 : 0;
        int days = 1;
        Matcher chineseDays = Pattern.compile("\u672a\u6765\\s*(\\d+)\\s*\u5929").matcher(normalized);
        Matcher englishDays = Pattern.compile("next\\s*(\\d+)\\s*days?", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (chineseDays.find()) {
            days = clampDays(chineseDays.group(1));
        } else if (englishDays.find()) {
            days = clampDays(englishDays.group(1));
        }
        return new WeatherIntent(true, extractStableWeatherLocation(normalized), dayOffset, days);
    }

    private String extractStableWeatherLocation(String message) {
        Matcher chineseMatcher = WEATHER_CHINESE_LOCATION_PATTERN.matcher(message);
        if (chineseMatcher.find()) {
            String candidate = normalizeStableWeatherLocation(chineseMatcher.group(1));
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        Matcher englishMatcher = ENGLISH_LOCATION_PATTERN.matcher(message);
        if (englishMatcher.find()) {
            return normalizeStableWeatherLocation(englishMatcher.group(1));
        }
        return "";
    }

    private String normalizeStableWeatherLocation(String candidate) {
        if (candidate == null) {
            return "";
        }
        return candidate
                .replace("\u5e2e\u6211", "")
                .replace("\u67e5\u8be2\u4e0b", "")
                .replace("\u67e5\u4e00\u4e0b", "")
                .replace("\u67e5\u4e0b", "")
                .replace("\u770b\u4e0b", "")
                .replace("\u770b\u770b", "")
                .replace("\u4e00\u4e0b", "")
                .replace("\u7684", "")
                .replace("\u5929\u6c14", "")
                .trim();
    }

    private String renderWeatherAnswer(String userMessage, com.fasterxml.jackson.databind.JsonNode weatherPayload) {
        String location = weatherPayload.path("location").asText("the requested location");
        int dayOffset = weatherPayload.path("dayOffset").asInt(0);
        String label = switch (dayOffset) {
            case 1 -> containsChinese(userMessage) ? "明天" : "tomorrow";
            case 2 -> containsChinese(userMessage) ? "后天" : "the day after tomorrow";
            default -> containsChinese(userMessage) ? "今天" : "today";
        };
        String condition = weatherPayload.path("condition").asText("");
        String min = weatherPayload.path("temperatureMin").asText("");
        String max = weatherPayload.path("temperatureMax").asText("");
        String wind = weatherPayload.path("wind").asText("");
        String precipitation = weatherPayload.path("precipitation").asText("");
        if (containsChinese(userMessage)) {
            StringBuilder builder = new StringBuilder();
            builder.append(location).append(label).append("的天气：").append(System.lineSeparator())
                    .append("- 天气：").append(condition.isBlank() ? "暂无数据" : condition).append(System.lineSeparator())
                    .append("- 气温：").append(min).append("°C ~ ").append(max).append("°C").append(System.lineSeparator());
            if (!wind.isBlank()) {
                builder.append("- 风况：").append(wind).append(System.lineSeparator());
            }
            if (!precipitation.isBlank()) {
                builder.append("- 降水：").append(precipitation).append(System.lineSeparator());
            }
            builder.append(System.lineSeparator()).append("来源：wttr.in");
            return builder.toString().trim();
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Weather for ").append(location).append(" ").append(label).append(":").append(System.lineSeparator())
                .append("- Condition: ").append(condition.isBlank() ? "n/a" : condition).append(System.lineSeparator())
                .append("- Temperature: ").append(min).append("°C to ").append(max).append("°C").append(System.lineSeparator());
        if (!wind.isBlank()) {
            builder.append("- Wind: ").append(wind).append(System.lineSeparator());
        }
        if (!precipitation.isBlank()) {
            builder.append("- Precipitation: ").append(precipitation).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator()).append("Source: wttr.in");
        return builder.toString().trim();
    }

    private WeatherIntent parseWeatherIntent(String message) {
        if (message == null || message.isBlank()) {
            return new WeatherIntent(false, "", 0, 1);
        }
        String normalized = message.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        boolean isWeather = containsAny(lower,
                "weather", "forecast", "temperature", "rain", "澶╂皵", "姘旀俯", "娓╁害", "闄嶉洦", "涓嬮洦");
        if (!isWeather) {
            return new WeatherIntent(false, "", 0, 1);
        }
        int dayOffset = containsAny(lower, "后天", "the day after tomorrow") ? 2
                : containsAny(lower, "tomorrow", "明天") ? 1 : 0;
        int days = 1;
        Matcher chineseDays = Pattern.compile("未来\\s*(\\d+)\\s*天").matcher(normalized);
        Matcher englishDays = Pattern.compile("next\\s*(\\d+)\\s*days?", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (chineseDays.find()) {
            days = clampDays(chineseDays.group(1));
        } else if (englishDays.find()) {
            days = clampDays(englishDays.group(1));
        }
        return new WeatherIntent(true, extractWeatherLocation(normalized), dayOffset, days);
    }

    private int clampDays(String rawValue) {
        try {
            return Math.max(1, Math.min(Integer.parseInt(rawValue), 3));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private String extractWeatherLocation(String message) {
        Matcher chineseMatcher = CHINESE_LOCATION_PATTERN.matcher(message);
        if (chineseMatcher.find()) {
            String candidate = normalizeWeatherLocation(chineseMatcher.group(1));
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        Matcher englishMatcher = ENGLISH_LOCATION_PATTERN.matcher(message);
        if (englishMatcher.find()) {
            return normalizeWeatherLocation(englishMatcher.group(1));
        }
        return "";
    }

    private String normalizeWeatherLocation(String candidate) {
        if (candidate == null) {
            return "";
        }
        return candidate
                .replace("帮我", "")
                .replace("请", "")
                .replace("查一下", "")
                .replace("查下", "")
                .replace("看看", "")
                .replace("看下", "")
                .replace("帮我看下", "")
                .replace("帮我查下", "")
                .replace("天气", "")
                .trim();
    }

    private String domainOf(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost().replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private ExplorationResult exploreDocuments(AgentExecutionRequest request,
                                               List<DocumentRecord> documents,
                                               String planText,
                                               AgentOutputEmitter outputEmitter) {
        List<String> actions = new ArrayList<>();
        List<RetrievedChunk> retrievedChunks = contextAssembler.retrieve(
                request.message(),
                documents,
                document -> chunkIndexStore.readChunkIndex(request.userId(), request.threadId(), "documents/%s/chunks.json".formatted(document.documentId())),
                8
        );
        actions.add("lexical retrieval on user query");
        outputEmitter.emitEvent(RunEventType.EXPLORE_ACTION, Map.of(
                "runId", request.runId(),
                "action", "lexical-retrieval",
                "matchedChunks", retrievedChunks.size()
        ));

        if (retrievedChunks.isEmpty() && !planText.isBlank()) {
            retrievedChunks = contextAssembler.retrieve(
                    planText,
                    documents,
                    document -> chunkIndexStore.readChunkIndex(request.userId(), request.threadId(), "documents/%s/chunks.json".formatted(document.documentId())),
                    8
            );
            actions.add("plan-guided lexical retrieval");
            outputEmitter.emitEvent(RunEventType.EXPLORE_ACTION, Map.of(
                    "runId", request.runId(),
                    "action", "plan-guided-retrieval",
                    "matchedChunks", retrievedChunks.size()
            ));
        }

        if (retrievedChunks.isEmpty()) {
            List<RetrievedChunk> fallbackChunks = new ArrayList<>();
            for (DocumentRecord document : documents) {
                List<DocumentChunk> chunks = chunkIndexStore.readChunkIndex(
                        request.userId(),
                        request.threadId(),
                        "documents/%s/chunks.json".formatted(document.documentId())
                );
                int limit = Math.min(2, chunks.size());
                for (int index = 0; index < limit; index++) {
                    fallbackChunks.add(new RetrievedChunk(chunks.get(index), Math.max(1, 10 - index)));
                }
            }
            retrievedChunks = fallbackChunks.stream()
                    .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                    .limit(8)
                    .toList();
            actions.add("lead-document fallback");
            outputEmitter.emitEvent(RunEventType.EXPLORE_ACTION, Map.of(
                    "runId", request.runId(),
                    "action", "lead-document-fallback",
                    "matchedChunks", retrievedChunks.size()
            ));
        }

        String workingMemory = retrievedChunks.isEmpty()
                ? "Exploration found no readable excerpts after lexical retrieval and lead-document fallback."
                : "Exploration gathered %d evidence chunk(s) after actions: %s.".formatted(
                retrievedChunks.size(),
                String.join(", ", actions)
        );
        return new ExplorationResult(retrievedChunks, List.copyOf(actions), workingMemory);
    }

    private void writeResearchArtifacts(AgentExecutionRequest request,
                                        AgentOutputEmitter outputEmitter,
                                        ExplorationResult explorationResult) {
        try {
            Path retrievedPath = workspaceManager.resolvePath(request.userId(), request.threadId(), WorkspaceArea.WORKSPACE, request.runId() + "/research/retrieved_chunks.json");
            Files.createDirectories(retrievedPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(retrievedPath.toFile(), explorationResult.retrievedChunks());
            String workspaceId = threadRuntimeService.getThread(request.userId(), request.threadId()).workspaceId();
            ArtifactRecord retrievedArtifact = artifactService.register(new RegisterArtifactCommand(
                    request.userId(),
                    workspaceId,
                    request.threadId(),
                    "retrieved_chunks.json",
                    ArtifactType.INTERMEDIATE_FILE,
                    ArtifactVisibility.INTERNAL,
                    WorkspaceArea.WORKSPACE,
                    request.runId() + "/research/retrieved_chunks.json",
                    "application/json"
            ));
            outputEmitter.emitEvent(RunEventType.ARTIFACT_CREATED, retrievedArtifact);

            String retrievalNotes = """
                    # Retrieval Notes

                    ## Actions
                    %s

                    ## Working Memory
                    %s
                    """.formatted(
                    explorationResult.actions().isEmpty()
                            ? "- none"
                            : explorationResult.actions().stream().map(action -> "- " + action).reduce((left, right) -> left + System.lineSeparator() + right).orElse("- none"),
                    explorationResult.workingMemory()
            );
            writeNoteArtifact(request, outputEmitter, request.runId() + "/research/retrieval_notes.md", "retrieval_notes.md", retrievalNotes);

            String keyExcerpts = explorationResult.retrievedChunks().isEmpty()
                    ? "No retrieved excerpts."
                    : explorationResult.retrievedChunks().stream()
                    .map(chunk -> "### [%s, p.%d]\n\n%s".formatted(
                            chunk.chunk().documentName(),
                            chunk.chunk().pageStart(),
                            chunk.chunk().text()
                    ))
                    .reduce((left, right) -> left + System.lineSeparator() + System.lineSeparator() + right)
                    .orElse("");
            writeNoteArtifact(request, outputEmitter, request.runId() + "/research/key_excerpts.md", "key_excerpts.md", keyExcerpts);

            Set<String> sources = new LinkedHashSet<>();
            for (RetrievedChunk retrievedChunk : explorationResult.retrievedChunks()) {
                sources.add("- [%s, p.%d]".formatted(retrievedChunk.chunk().documentName(), retrievedChunk.chunk().pageStart()));
            }
            String brief = """
                    # Analysis Brief

                    Retrieved %d evidence chunks.

                    ## Sources
                    %s
                    """.formatted(
                    explorationResult.retrievedChunks().size(),
                    sources.isEmpty() ? "- none" : String.join(System.lineSeparator(), sources)
            );
            writeNoteArtifact(request, outputEmitter, request.runId() + "/research/analysis_brief.md", "analysis_brief.md", brief);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write research artifacts", exception);
        }
    }

    private ArtifactRecord writeNoteArtifact(AgentExecutionRequest request,
                                             AgentOutputEmitter outputEmitter,
                                             String relativePath,
                                             String artifactName,
                                             String content) {
        try {
            Path path = writeOutput(request, relativePath, content);
            String workspaceId = threadRuntimeService.getThread(request.userId(), request.threadId()).workspaceId();
            ArtifactRecord artifact = artifactService.register(new RegisterArtifactCommand(
                    request.userId(),
                    workspaceId,
                    request.threadId(),
                    artifactName,
                    ArtifactType.NOTE,
                    ArtifactVisibility.USER_VISIBLE,
                    WorkspaceArea.OUTPUTS,
                    workspaceManager.areaRoot(request.userId(), request.threadId(), WorkspaceArea.OUTPUTS).relativize(path).toString().replace('\\', '/'),
                    "text/markdown"
            ));
            outputEmitter.emitEvent(RunEventType.ARTIFACT_CREATED, artifact);
            return artifact;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write note artifact", exception);
        }
    }

    private Path writeOutput(AgentExecutionRequest request, String relativePath, String content) throws IOException {
        Path path = workspaceManager.resolvePath(request.userId(), request.threadId(), WorkspaceArea.OUTPUTS, relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    /*
    private List<SkillDefinition> recommendSkills(AgentMode mode,
                                                  String userId,
                                                  String message,
                                                  List<DocumentRecord> documents,
                                                  int uploadedFileCount,
                                                  SkillRuntimeSnapshot snapshot) {
        List<SkillDefinition> recommended = new ArrayList<>();
        boolean hasDocs = !documents.isEmpty() || uploadedFileCount > 0 || mode == AgentMode.DOCS;
        String normalized = message == null ? "" : message.toLowerCase();
        if (containsAny(normalized, "research", "investigate", "analysis", "report", "market", "trend", "compare", "versus", "vs", "competition", "competitor", "landscape")) {
            maybeAddSkill(userId, recommended, "research.deep-research", snapshot);
        }
        if (containsAny(normalized, "weather", "forecast", "temperature", "rain", "today", "tomorrow", "澶╂皵", "姘旀俯", "娓╁害", "鏄庡ぉ", "浠婂ぉ")) {
            maybeAddSkill(userId, recommended, "weather", snapshot);
        }
        if (hasDocs) {
            if (normalized.contains("compare") || normalized.contains("对比")) {
                maybeAddSkill(userId, recommended, "docs.compare-papers", snapshot);
            } else {
                maybeAddSkill(userId, recommended, "docs.paper-review", snapshot);
            }
        }
        if (normalized.contains("web") || normalized.contains("search") || normalized.contains("搜索")) {
            maybeAddSkill(userId, recommended, "docs.web-research", snapshot);
        }
        if (containsAny(normalized, "github", "github.com", "repo", "repository", "open source", "oss")) {
            maybeAddSkill(userId, recommended, "research.github-repo", snapshot);
        }
        if (containsAny(normalized, "compare", "vs", "versus", "competitive", "competition", "competitor", "landscape", "vendor")) {
            maybeAddSkill(userId, recommended, "research.competitive-analysis", snapshot);
        }
        return recommended.stream().distinct().toList();
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private void maybeAddSkill(String userId, List<SkillDefinition> skills, String skillId, SkillRuntimeSnapshot snapshot) {
        try {
            skills.add(skillRegistry.requireEnabledSkill(userId, skillId, snapshot));
        } catch (RuntimeException ignored) {
        }
    }

    private void emitSkillSelection(AgentOutputEmitter outputEmitter, SkillSelection selection) {
        for (SkillDefinition skill : selection.effectiveSkills()) {
            String reason = selection.manualSkills().stream().anyMatch(manual -> manual.skillId().equals(skill.skillId()))
                    ? "user-selected"
                    : "recommended";
            outputEmitter.emitEvent(RunEventType.SKILL_SELECTED, Map.of(
                    "skillId", skill.skillId(),
                    "agent", skill.agent(),
                    "reason", reason
            ));
        }
    }

    private List<SkillDefinition> loadSkillsForWorkflow(AgentExecutionRequest request,
                                                        List<SkillDefinition> effectiveSkills,
                                                        SkillRuntimeSnapshot snapshot,
                                                        AgentOutputEmitter outputEmitter) {
        List<SkillDefinition> loadedSkills = new ArrayList<>();
        for (SkillDefinition skill : effectiveSkills) {
            SkillDefinition loaded = skillRegistry.requireEnabledSkill(request.userId(), skill.skillId(), snapshot);
            loadedSkills.add(loaded);
            outputEmitter.emitEvent(RunEventType.SKILL_SOURCE_RESOLVED, Map.of(
                    "skillId", loaded.skillId(),
                    "sourceKey", loaded.sourceKey(),
                    "source", loaded.source()
            ));
            outputEmitter.emitEvent(RunEventType.SKILL_ACTIVATED, Map.of(
                    "skillId", loaded.skillId(),
                    "sourceKey", loaded.sourceKey(),
                    "execution", loaded.execution().configValue()
            ));
            outputEmitter.emitEvent(RunEventType.SKILL_LOADED, Map.of(
                    "skillId", loaded.skillId(),
                    "source", loaded.source(),
                    "path", loaded.sourcePath().toString(),
                    "loadReason", "workflow-explicit"
            ));
        }
        return List.copyOf(loadedSkills);
    }

    private SkillRuntimeSupport.ActivatedSkillContext activateSkills(AgentExecutionRequest request,
                                                                     List<SkillDefinition> effectiveSkills,
                                                                     SkillRuntimeSnapshot snapshot,
                                                                     List<ToolDescriptor> availableTools,
                                                                     AgentOutputEmitter outputEmitter) {
        List<SkillDefinition> loadedSkills = loadSkillsForWorkflow(request, effectiveSkills, snapshot, outputEmitter);
        return SkillRuntimeSupport.activate(loadedSkills, availableTools);
    }

    */
    private SkillRuntimeSnapshot snapshotFor(AgentExecutionRequest request) {
        return request.skillRuntimeSnapshot() == null
                ? skillRegistry.snapshotForUser(request.userId())
                : request.skillRuntimeSnapshot();
    }

    private AgentExecutionRequest withRuntimeContext(AgentExecutionRequest request,
                                                     SkillRuntimeSnapshot snapshot,
                                                     ToolUseLimits toolUseLimits) {
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
                effectiveToolUseLimits(request.chatRouteKind(), toolUseLimits),
                request.activeSkillIds(),
                request.selectedDocumentIds()
        );
    }

    private ToolUseLimits effectiveToolUseLimits(ChatRouteKind routeKind, ToolUseLimits existingLimits) {
        if (existingLimits != null) {
            return existingLimits;
        }
        if (routeKind == ChatRouteKind.DOCUMENT_QA || routeKind == ChatRouteKind.RESEARCH_DRAFT) {
            return null;
        }
        return new ToolUseLimits(
                chatMaxToolCalls,
                chatMaxSearchCalls,
                chatMaxFetchCalls,
                0,
                chatMinVerifiedSources,
                chatTimeoutMs
        );
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldUseDocsWorkflow(AgentMode mode,
                                          AgentExecutionRequest request,
                                          List<DocumentRecord> documents) {
        if (mode == AgentMode.DOCS) {
            return true;
        }
        return !documents.isEmpty() || !request.uploadedFiles().isEmpty();
    }

    private String clarificationQuestion(AgentExecutionRequest request,
                                         List<DocumentRecord> documents) {
        String normalized = request.message() == null ? "" : request.message().toLowerCase(Locale.ROOT);
        boolean compareSkill = normalized.contains("compare")
                || normalized.contains("versus")
                || normalized.contains("vs")
                || normalized.contains("瀵规瘮");
        if (compareSkill) {
            int documentCount = Math.max(documents.size(), request.uploadedFiles().size());
            if (documentCount < 2) {
                return containsChinese(request.message())
                        ? "要进行论文对比，请至少上传两篇可读取的文档。"
                        : "Please upload at least two readable documents before asking for a comparison.";
            }
        }
        if (request.chatRouteKind() == ChatRouteKind.DOCUMENT_QA
                && documents.isEmpty()
                && request.uploadedFiles().isEmpty()) {
            return containsChinese(request.message())
                    ? "请先上传要分析的文档，或明确告诉我希望处理的材料。"
                    : "Please upload the document you want analyzed, or clarify what material I should work on.";
        }
        return null;
    }

    private String buildPrompt(AgentMode mode,
                               AgentExecutionRequest request,
                               ChatRouteKind routeKind,
                               List<DocumentRecord> documents,
                               List<RetrievedChunk> retrievedChunks,
                               String phase,
                               List<String> actions,
                               String workingMemory,
                               List<SkillDescriptor> availableSkills,
                               List<ToolDescriptor> availableTools) {
        AgentPromptRequest promptRequest = new AgentPromptRequest(
                agentId(mode),
                request.message(),
                routeKind == null ? ChatRouteKind.CHAT : routeKind,
                List.of(),
                availableTools,
                request.recentMessages(),
                List.of(),
                request.uploadedFiles(),
                request.artifacts(),
                documents,
                retrievedChunks,
                request.sessionSummary(),
                request.longTermMemory(),
                phase,
                actions,
                workingMemory,
                request.selectedDocumentIds(),
                "",
                List.of(),
                availableSkills,
                List.of(),
                resolveLoadedSkills(request)
        );
        String prompt = agentPromptService.renderPrompt(promptRequest);
        if (logPrompts) {
            logger.info(() -> "Prompt for run " + request.runId()
                    + " on thread " + request.threadId()
                    + ":" + System.lineSeparator()
                    + prompt);
        }
        return prompt;
    }

    private List<ToolDescriptor> availableChatTools(String userId) {
        return List.copyOf(agentToolService.listAvailableTools(userId));
    }

    private List<SkillDefinition> resolveLoadedSkills(AgentExecutionRequest request) {
        if (request.activeSkillIds() == null || request.activeSkillIds().isEmpty()) {
            return List.of();
        }
        SkillRuntimeSnapshot snapshot = skillRegistry.snapshotForUser(request.userId());
        List<SkillDefinition> loadedSkills = new ArrayList<>();
        for (String skillId : request.activeSkillIds()) {
            if (skillId == null || skillId.isBlank()) {
                continue;
            }
            try {
                loadedSkills.add(skillRegistry.requireEnabledSkill(request.userId(), skillId, snapshot));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale ids when rebuilding prompt state.
            }
        }
        return List.copyOf(loadedSkills);
    }

    private ChatRouteKind normalizeChatRoute(ChatRouteKind routeKind) {
        return routeKind == null ? ChatRouteKind.CHAT : routeKind;
    }

    private void emitStage(AgentOutputEmitter outputEmitter, RunEventType eventType, String stage) {
        outputEmitter.emitEvent(eventType, Map.of("stage", stage));
    }

    private Iterable<String> split(String response, int chunkSize) {
        List<String> segments = new ArrayList<>();
        for (int index = 0; index < response.length(); index += chunkSize) {
            int end = Math.min(response.length(), index + chunkSize);
            segments.add(response.substring(index, end));
        }
        return segments;
    }

    private String summarize(String response) {
        String normalized = response.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private String agentId(AgentMode mode) {
        return mode == AgentMode.DOCS ? "docs-agent" : "general-agent";
    }

    private AgentCapability capability(AgentMode mode) {
        return mode == AgentMode.DOCS ? AgentCapability.DOCS : AgentCapability.GENERAL;
    }

    private boolean containsChinese(String text) {
        return text != null && text.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private String enforceCitations(String response, List<RetrievedChunk> retrievedChunks, boolean hasDocuments) {
        if (retrievedChunks.isEmpty()) {
            if (!hasDocuments) {
                return response;
            }
            String normalized = response.trim();
            if (normalized.toLowerCase().contains("limited evidence") || normalized.contains("证据有限")) {
                return normalized;
            }
            return normalized + System.lineSeparator() + System.lineSeparator()
                    + "Evidence note: analysis used limited or fallback document evidence; claims without inline citations should be treated as tentative.";
        }
        if (response.contains("[") && response.contains("p.")) {
            return response;
        }
        StringBuilder builder = new StringBuilder(response.trim());
        builder.append(System.lineSeparator()).append(System.lineSeparator()).append("Sources").append(System.lineSeparator());
        Set<String> sources = new LinkedHashSet<>();
        for (RetrievedChunk retrievedChunk : retrievedChunks) {
            sources.add("- [%s, p.%d]".formatted(retrievedChunk.chunk().documentName(), retrievedChunk.chunk().pageStart()));
        }
        for (String source : sources) {
            builder.append(source).append(System.lineSeparator());
        }
        return builder.toString().trim();
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

    private String renderResearchUnitResult(ResearchUnitResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(result.title()).append("\n\n")
                .append("## Query\n").append(result.query()).append("\n\n")
                .append("## Notes\n").append(result.notes()).append("\n\n")
                .append("## Local Conclusion\n").append(result.localConclusion()).append("\n\n")
                .append("## Sources\n");
        if (result.sources().isEmpty()) {
            builder.append("- none");
        } else {
            for (String source : result.sources()) {
                builder.append("- ").append(source).append("\n");
            }
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

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private void logFlow(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(messageSupplier);
        }
    }

    private record ExplorationResult(
            List<RetrievedChunk> retrievedChunks,
            List<String> actions,
            String workingMemory
    ) {
    }

    private record LookupResult(
            List<ExecutionSource> candidateSources,
            List<ExecutionSource> verifiedSources,
            List<String> actions,
            String workingMemory,
            com.fasterxml.jackson.databind.JsonNode weatherPayload,
            String weatherError
    ) {
    }

    private record WeatherIntent(
            boolean isWeather,
            String location,
            int dayOffset,
            int days
    ) {
    }
}
