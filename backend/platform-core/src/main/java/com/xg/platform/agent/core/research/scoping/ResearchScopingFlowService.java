package com.xg.platform.agent.core.research.scoping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.AgentTurnExecutionSupport;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.contracts.message.ResearchDraftRecord;
import com.xg.platform.contracts.message.ResearchDraftStatus;
import com.xg.platform.contracts.message.ResearchPlanStep;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.message.RunEventType;
import com.xg.platform.graph.ResearchScopingState;
import com.xg.platform.runtime.MemoryEventPayload;
import com.xg.platform.runtime.MemoryEventPublisher;
import com.xg.platform.runtime.MessageRepository;
import com.xg.platform.runtime.ResearchDraftRepository;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.workspace.ArtifactService;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ResearchScopingFlowService {

    private static final Logger logger = Logger.getLogger(ResearchScopingFlowService.class.getName());

    private final ThreadRuntimeService threadRuntimeService;
    private final MessageRepository messageRepository;
    private final ResearchDraftRepository researchDraftRepository;
    private final RunEventRepository runEventRepository;
    private final MemoryEventPublisher memoryEventPublisher;
    private final ArtifactService artifactService;
    private final AgentTurnExecutionSupport agentTurnExecutionSupport;
    private final ObjectMapper objectMapper;
    private final boolean logAgentFlow;

    public ResearchScopingFlowService(ThreadRuntimeService threadRuntimeService,
                                      MessageRepository messageRepository,
                                      ResearchDraftRepository researchDraftRepository,
                                      RunEventRepository runEventRepository,
                                      MemoryEventPublisher memoryEventPublisher,
                                      ArtifactService artifactService,
                                      AgentTurnExecutionSupport agentTurnExecutionSupport,
                                      ObjectMapper objectMapper,
                                      boolean logAgentFlow) {
        this.threadRuntimeService = threadRuntimeService;
        this.messageRepository = messageRepository;
        this.researchDraftRepository = researchDraftRepository;
        this.runEventRepository = runEventRepository;
        this.memoryEventPublisher = memoryEventPublisher;
        this.artifactService = artifactService;
        this.agentTurnExecutionSupport = agentTurnExecutionSupport;
        this.objectMapper = objectMapper;
        this.logAgentFlow = logAgentFlow;
    }

    public Map<String, Object> runScopingFrame(String userId,
                                               String threadId,
                                               PostMessageRequest request,
                                               ThreadMemoryView memoryView,
                                               String longTermMemory,
                                               ResearchDraftRecord currentDraft,
                                               Consumer<RunEvent> runEventConsumer) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("Message content must not be blank");
        }
        String runId = UUID.randomUUID().toString();
        MessageRecord userMessage = persistMessage(
                userId,
                threadId,
                MessageRole.USER,
                request.content(),
                InteractionMode.DEEP_RESEARCH,
                runId,
                null
        );
        publishEvent(userId, threadId, runId, RunEventType.MESSAGE_ACCEPTED, Map.of(
                "messageId", userMessage.messageId(),
                "interactionMode", InteractionMode.DEEP_RESEARCH.name()
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.RUN_STARTED, Map.of(
                "providerId", agentTurnExecutionSupport.resolveProviderId(userId, request.providerId()),
                "interactionMode", InteractionMode.DEEP_RESEARCH.name(),
                "routeKind", ChatRouteKind.RESEARCH_DRAFT.name(),
                "workflow", "research-draft",
                "toolsEnabled", false
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.ROUTE_SELECTED, Map.of(
                "interactionMode", InteractionMode.DEEP_RESEARCH.name(),
                "routeKind", ChatRouteKind.RESEARCH_DRAFT.name(),
                "workflow", "research-draft",
                "toolsEnabled", false
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.DRAFT_EDITING_STARTED, Map.of(
                "draftId", currentDraft == null ? "" : currentDraft.draftId(),
                "workflow", "research-draft"
        ), runEventConsumer);

        ScopingResponse scoping = runScoping(
                userId,
                threadId,
                request.providerId(),
                request.content(),
                currentDraft,
                memoryView,
                longTermMemory
        );
        logFlow(() -> "runScopingFrame thread=" + threadId
                + " run=" + runId
                + " ready=" + scoping.ready()
                + " questions=" + scoping.questions().size()
                + " objective=" + summarize(scoping.objective()));
        return Map.of(
                ResearchScopingState.RUN_ID, runId,
                ResearchScopingState.USER_MESSAGE, userMessage,
                ResearchScopingState.SCOPING_RESPONSE, scoping,
                ResearchScopingState.ASSISTANT_CONTENT, formatScopingMessage(scoping)
        );
    }

    public Map<String, Object> persistDraft(ResearchScopingState state) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        ResearchDraftRecord currentDraft = state.<ResearchDraftRecord>currentDraft().orElse(null);
        ScopingResponse scoping = state.<ScopingResponse>scopingResponse()
                .orElseThrow(() -> new IllegalStateException("Scoping response is missing"));
        MessageRecord userMessage = state.<MessageRecord>userMessage()
                .orElseThrow(() -> new IllegalStateException("Scoping user message is missing"));

        Instant now = Instant.now();
        ResearchDraftRecord nextDraft = new ResearchDraftRecord(
                currentDraft == null ? UUID.randomUUID().toString() : currentDraft.draftId(),
                threadId,
                scoping.ready() ? ResearchDraftStatus.READY : ResearchDraftStatus.COLLECTING,
                scoping.title(),
                scoping.brief(),
                scoping.objective(),
                scoping.scope(),
                scoping.outputFormat(),
                scoping.constraints(),
                scoping.questions(),
                nextRevision(currentDraft),
                scoping.planSummary(),
                scoping.planSteps(),
                scoping.ready(),
                userMessage.messageId(),
                null,
                currentDraft == null ? now : currentDraft.createdAt(),
                now
        );
        researchDraftRepository.save(userId, nextDraft);
        return Map.of(ResearchScopingState.DRAFT_RECORD, nextDraft);
    }

    public Map<String, Object> persistAssistantMessage(ResearchScopingState state,
                                                       Consumer<RunEvent> runEventConsumer) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String runId = state.runId().orElseThrow();
        String assistantContent = state.assistantContent()
                .orElseThrow(() -> new IllegalStateException("Scoping assistant content is missing"));
        ResearchDraftRecord draft = state.<ResearchDraftRecord>draftRecord()
                .orElseThrow(() -> new IllegalStateException("Scoping draft is missing"));

        for (String segment : split(assistantContent, 180)) {
            publishEvent(userId, threadId, runId, RunEventType.MESSAGE_DELTA, Map.of("delta", segment), runEventConsumer);
        }

        MessageRecord assistantMessage = persistMessage(
                userId,
                threadId,
                MessageRole.ASSISTANT,
                assistantContent,
                InteractionMode.DEEP_RESEARCH,
                runId,
                null
        );
        ResearchDraftRecord updatedDraft = new ResearchDraftRecord(
                draft.draftId(),
                draft.threadId(),
                draft.status(),
                draft.title(),
                draft.brief(),
                draft.objective(),
                draft.scope(),
                draft.outputFormat(),
                draft.constraints(),
                draft.questions(),
                draft.revision(),
                draft.planSummary(),
                draft.planSteps(),
                draft.ready(),
                draft.lastUserMessageId(),
                assistantMessage.messageId(),
                draft.createdAt(),
                Instant.now()
        );
        researchDraftRepository.save(userId, updatedDraft);
        return Map.of(
                ResearchScopingState.ASSISTANT_MESSAGE, assistantMessage,
                ResearchScopingState.DRAFT_RECORD, updatedDraft
        );
    }

    public Map<String, Object> publishScopingEvents(ResearchScopingState state,
                                                    Consumer<RunEvent> runEventConsumer) {
        String userId = state.userId().orElseThrow();
        String threadId = state.threadId().orElseThrow();
        String runId = state.runId().orElseThrow();
        ResearchDraftRecord draft = state.<ResearchDraftRecord>draftRecord()
                .orElseThrow(() -> new IllegalStateException("Scoping draft is missing"));
        MessageRecord assistantMessage = state.<MessageRecord>assistantMessage()
                .orElseThrow(() -> new IllegalStateException("Scoping assistant message is missing"));

        publishEvent(userId, threadId, runId, RunEventType.RESEARCH_BRIEF_UPDATED, draft, runEventConsumer);
        publishMemoryEvent(RunEventType.RESEARCH_BRIEF_UPDATED, userId, threadId, null, draft.lastAssistantMessageId());
        publishEvent(userId, threadId, runId, RunEventType.RESEARCH_PLAN_PREVIEW_UPDATED, draft, runEventConsumer);
        if (draft.ready()) {
            publishEvent(userId, threadId, runId, RunEventType.RESEARCH_BRIEF_READY, Map.of(
                    "draftId", draft.draftId(),
                    "title", draft.title()
            ), runEventConsumer);
        } else {
            publishEvent(userId, threadId, runId, RunEventType.RESEARCH_QUESTIONS_REQUESTED, Map.of(
                    "draftId", draft.draftId(),
                    "questions", draft.questions()
            ), runEventConsumer);
        }
        publishEvent(userId, threadId, runId, RunEventType.MESSAGE_COMPLETED, Map.of(
                "messageId", assistantMessage.messageId()
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.DRAFT_EDITING_COMPLETED, Map.of(
                "draftId", draft.draftId(),
                "ready", draft.ready(),
                "revision", draft.revision()
        ), runEventConsumer);
        publishEvent(userId, threadId, runId, RunEventType.RUN_COMPLETED, Map.of(
                "summary", summarize(draft.planSummary().isBlank() ? draft.brief() : draft.planSummary()),
                "providerId", state.request()
                        .map(PostMessageRequest.class::cast)
                        .map(PostMessageRequest::providerId)
                        .map(providerId -> agentTurnExecutionSupport.resolveProviderId(userId, providerId))
                        .orElse(agentTurnExecutionSupport.resolveProviderId(userId, null)),
                "routeKind", ChatRouteKind.RESEARCH_DRAFT.name(),
                "workflow", "research-draft",
                "toolsEnabled", false,
                "finalMessageId", assistantMessage.messageId(),
                "sources", List.of(),
                "sourceCount", 0,
                "usedVerifiedSources", 0
        ), runEventConsumer);
        publishMemoryEvent(RunEventType.MESSAGE_COMPLETED, userId, threadId, null, assistantMessage.messageId());
        threadRuntimeService.touchThread(userId, threadId);
        return Map.of(
                ResearchScopingState.EVENTS_PUBLISHED, true,
                ResearchScopingState.RESULT, "completed"
        );
    }

    private ScopingResponse runScoping(String userId,
                                       String threadId,
                                       String providerId,
                                       String latestUserInput,
                                       ResearchDraftRecord currentDraft,
                                       ThreadMemoryView memoryView,
                                       String longTermMemory) {
        String resolvedProviderId = agentTurnExecutionSupport.resolveProviderId(userId, providerId);
        ResearchDraftRecord draft = currentDraft == null ? emptyDraft(threadId) : currentDraft;
        KnownScopingState knownState = inferKnownScopingState(draft, latestUserInput, renderRecentConversation(memoryView));
        ScopingFrame frame = runScopingFraming(
                userId,
                threadId,
                resolvedProviderId,
                latestUserInput,
                draft,
                knownState,
                memoryView,
                longTermMemory
        );
        List<String> filteredMissingDecisionTypes = filterMissingDecisionTypes(frame.missingDecisionTypes(), knownState);
        boolean ready = filteredMissingDecisionTypes.isEmpty() || frame.ready();
        List<String> questions = ready
                ? List.of()
                : runClarificationQuestions(userId, resolvedProviderId, latestUserInput, draft, knownState, frame, filteredMissingDecisionTypes);
        if (questions.isEmpty() && !ready) {
            ready = true;
        }
        boolean hasArtifacts = !artifactService.listArtifacts(userId, threadId).isEmpty();
        String brief = buildScopingBrief(frame);
        return new ScopingResponse(
                mergeText(frame.title(), draft.title(), "Deep research"),
                brief,
                mergeText(frame.objective(), draft.objective(), ""),
                mergeText(frame.scope(), draft.scope(), ""),
                mergeText(frame.outputFormat(), knownState.outputFormat(), draft.outputFormat()),
                mergeConstraints(frame.constraints(), knownState.constraints(), draft.constraints()),
                mergeText(frame.planSummary(), draft.planSummary(), summarize(brief)),
                frame.planSteps().isEmpty() ? defaultPlanSteps(latestUserInput, hasArtifacts) : frame.planSteps(),
                questions,
                ready
        );
    }

    private ScopingFrame runScopingFraming(String userId,
                                           String threadId,
                                           String providerId,
                                           String latestUserInput,
                                           ResearchDraftRecord draft,
                                           KnownScopingState knownState,
                                           ThreadMemoryView memoryView,
                                           String longTermMemory) {
        String prompt = """
                You are preparing a deep research brief for a multi-step research agent.
                The user has explicitly chosen deep research. This is the scoping phase only.
                Do not write the research report. Do not produce a final answer.
                Expand the likely research dimensions and determine whether any high-value clarification
                is still needed before background research starts.

                Return strict JSON with keys:
                title: string
                objective: string
                scope: string
                outputFormat: string
                constraints: string[]
                researchUnderstanding: string
                planSummary: string
                planSteps: array of objects with keys:
                  title: string
                  objective: string
                  query: string
                  useWeb: boolean
                  useDocuments: boolean
                  outputFocus: string
                missingDecisionTypes: string[]
                ready: boolean

                Rules:
                - researchUnderstanding should be a concise framing of the user's request, not a report.
                - planSummary should describe the overall research approach in 1 to 2 sentences.
                - planSteps should contain 2 to 5 concrete research steps.
                - planSteps should be specific to the topic, not generic placeholders.
                - missingDecisionTypes must only use:
                  ["output_format","scope_emphasis","time_range","source_preference","audience_depth","region_focus"]
                - Only include missingDecisionTypes that materially change the plan and are not already answered.
                - If output format is already known or there are no explicit constraints, do not ask again.
                - ready=true when research can start with sensible defaults and no critical ambiguity remains.
                """;
        String userPrompt = """
                Current research draft:
                title: %s
                brief: %s
                objective: %s
                scope: %s
                outputFormat: %s
                constraints: %s
                questions: %s

                Known answered decisions:
                outputFormat: %s
                noExplicitConstraints: %s
                explicitConstraints: %s

                Session summary:
                %s

                Long-term memory:
                %s

                Latest user input:
                %s

                Recent conversation:
                %s

                Thread has %d uploaded artifacts.
                """.formatted(
                draft.title(),
                draft.brief(),
                draft.objective(),
                draft.scope(),
                draft.outputFormat(),
                draft.constraints(),
                draft.questions(),
                knownState.outputFormat(),
                knownState.noExplicitConstraints(),
                knownState.constraints(),
                nullSafe(memoryView.summary()),
                nullSafe(longTermMemory),
                latestUserInput,
                renderRecentConversation(memoryView),
                artifactService.listArtifacts(userId, threadId).size()
        );
        String response = agentTurnExecutionSupport.runTextTurn(userId, providerId, prompt, userPrompt);
        logFlow(() -> "runScopingFraming thread=" + threadId
                + " provider=" + providerId
                + " hasText=" + !response.isBlank());
        if (response.isBlank()) {
            return fallbackScopingFrame(latestUserInput, draft, knownState);
        }
        try {
            JsonNode node = parseJsonObject(response);
            return new ScopingFrame(
                    textOrDefault(node, "title", draft.title()),
                    textOrDefault(node, "objective", draft.objective()),
                    textOrDefault(node, "scope", draft.scope()),
                    textOrDefault(node, "outputFormat", knownState.outputFormat()),
                    readStringList(node.path("constraints")),
                    textOrDefault(node, "researchUnderstanding", latestUserInput),
                    textOrDefault(node, "planSummary", draft.planSummary()),
                    readPlanSteps(node.path("planSteps"), !artifactService.listArtifacts(userId, threadId).isEmpty()),
                    readStringList(node.path("missingDecisionTypes")),
                    node.path("ready").asBoolean(false)
            );
        } catch (JsonProcessingException exception) {
            return fallbackScopingFrame(latestUserInput, draft, knownState);
        }
    }

    private List<String> runClarificationQuestions(String userId,
                                                   String providerId,
                                                   String latestUserInput,
                                                   ResearchDraftRecord draft,
                                                   KnownScopingState knownState,
                                                   ScopingFrame frame,
                                                   List<String> filteredMissingDecisionTypes) {
        String prompt = """
                You are asking clarification questions for a deep research brief.
                The user already chose deep research. Ask only the minimum high-value questions needed
                to finalize the plan. Do not ask generic template questions.

                Return strict JSON with keys:
                questions: string[]

                Rules:
                - Ask at most 3 questions.
                - Prefer topic-specific questions.
                - Do not ask about output format if it is already known.
                - Do not ask about source or time restrictions if the user already said there are no constraints.
                - Questions must be short and directly answerable.
                """;
        String userPrompt = """
                Research framing:
                objective: %s
                scope: %s
                outputFormat: %s
                researchUnderstanding: %s
                planSummary: %s
                planSteps: %s

                Missing decision types:
                %s

                Known answered decisions:
                outputFormat: %s
                noExplicitConstraints: %s
                explicitConstraints: %s

                Current draft questions:
                %s

                Latest user input:
                %s
                """.formatted(
                frame.objective(),
                frame.scope(),
                mergeText(frame.outputFormat(), knownState.outputFormat(), draft.outputFormat()),
                frame.researchUnderstanding(),
                frame.planSummary(),
                frame.planSteps(),
                filteredMissingDecisionTypes,
                knownState.outputFormat(),
                knownState.noExplicitConstraints(),
                knownState.constraints(),
                draft.questions(),
                latestUserInput
        );
        String response = agentTurnExecutionSupport.runTextTurn(userId, providerId, prompt, userPrompt);
        if (response.isBlank()) {
            return fallbackClarificationQuestions(knownState, filteredMissingDecisionTypes);
        }
        try {
            JsonNode node = parseJsonObject(response);
            List<String> questions = readStringList(node.path("questions"));
            if (questions.isEmpty()) {
                return fallbackClarificationQuestions(knownState, filteredMissingDecisionTypes);
            }
            return filterDuplicateQuestions(questions);
        } catch (JsonProcessingException exception) {
            return fallbackClarificationQuestions(knownState, filteredMissingDecisionTypes);
        }
    }

    private ResearchDraftRecord emptyDraft(String threadId) {
        Instant now = Instant.now();
        return new ResearchDraftRecord(
                UUID.randomUUID().toString(),
                threadId,
                ResearchDraftStatus.COLLECTING,
                "Deep research",
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                0,
                "",
                List.of(),
                false,
                null,
                null,
                now,
                now
        );
    }

    private ScopingFrame fallbackScopingFrame(String latestUserInput,
                                              ResearchDraftRecord draft,
                                              KnownScopingState knownState) {
        boolean useDocuments = !draft.planSteps().isEmpty()
                ? draft.planSteps().stream().anyMatch(ResearchPlanStep::useDocuments)
                : true;
        List<String> missingDecisionTypes = new ArrayList<>();
        if (knownState.outputFormat().isBlank()) {
            missingDecisionTypes.add("output_format");
        }
        return new ScopingFrame(
                mergeText(draft.title(), "Deep research", ""),
                mergeText(draft.objective(), "", ""),
                mergeText(draft.scope(), "Use a broad scope that covers context, evidence, caveats, and implications.", ""),
                mergeText(draft.outputFormat(), knownState.outputFormat(), ""),
                mergeConstraints(draft.constraints(), knownState.constraints(), List.of()),
                latestUserInput.trim(),
                mergeText(draft.planSummary(), "Research the topic in stages, gather evidence from the web and uploaded files, and synthesize the findings into a final report.", ""),
                defaultPlanSteps(latestUserInput, useDocuments),
                missingDecisionTypes,
                missingDecisionTypes.isEmpty()
        );
    }

    private String formatScopingMessage(ScopingResponse scoping) {
        StringBuilder builder = new StringBuilder();
        builder.append("Research plan preview\n\n");
        builder.append(scoping.brief()).append("\n\n");
        if (!nullSafe(scoping.objective()).isBlank()) {
            builder.append("Objective: ").append(scoping.objective()).append("\n");
        }
        if (!nullSafe(scoping.scope()).isBlank()) {
            builder.append("Scope: ").append(scoping.scope()).append("\n");
        }
        if (!nullSafe(scoping.outputFormat()).isBlank()) {
            builder.append("Output: ").append(scoping.outputFormat()).append("\n");
        }
        if (!scoping.constraints().isEmpty()) {
            builder.append("Constraints:\n");
            for (String constraint : scoping.constraints()) {
                builder.append("- ").append(constraint).append("\n");
            }
            builder.append("\n");
        }
        if (!nullSafe(scoping.planSummary()).isBlank()) {
            builder.append("Plan summary: ").append(scoping.planSummary()).append("\n\n");
        }
        if (!scoping.planSteps().isEmpty()) {
            builder.append("Planned steps:\n");
            for (int index = 0; index < scoping.planSteps().size(); index++) {
                ResearchPlanStep step = scoping.planSteps().get(index);
                builder.append(index + 1).append(". ").append(step.title()).append("\n")
                        .append("   - objective: ").append(step.objective()).append("\n")
                        .append("   - query: ").append(step.query()).append("\n")
                        .append("   - web: ").append(step.useWeb()).append("\n")
                        .append("   - documents: ").append(step.useDocuments()).append("\n")
                        .append("   - focus: ").append(step.outputFocus()).append("\n");
            }
            builder.append("\n");
        }
        if (!scoping.questions().isEmpty()) {
            builder.append("Before I start the background research, I need:\n");
            for (int index = 0; index < scoping.questions().size(); index++) {
                builder.append(index + 1).append(". ").append(scoping.questions().get(index)).append("\n");
            }
        } else if (scoping.ready()) {
            builder.append("The plan is ready. You can refine it with another Deep Research message, or start the background research now.");
        }
        return builder.toString().trim();
    }

    private String buildScopingBrief(ScopingFrame frame) {
        StringBuilder builder = new StringBuilder();
        builder.append(frame.researchUnderstanding().isBlank()
                ? "The request has been framed for deep research."
                : frame.researchUnderstanding().trim());
        if (!nullSafe(frame.planSummary()).isBlank()) {
            builder.append("\n\n").append(frame.planSummary().trim());
        }
        return builder.toString().trim();
    }

    private KnownScopingState inferKnownScopingState(ResearchDraftRecord draft,
                                                     String latestUserInput,
                                                     String recentConversation) {
        String combined = "%s%n%s%n%s%n%s%n%s".formatted(
                nullSafe(draft.outputFormat()),
                String.join(" ", draft.constraints()),
                nullSafe(draft.scope()),
                nullSafe(recentConversation),
                nullSafe(latestUserInput)
        );
        String outputFormat = mergeText(draft.outputFormat(), inferOutputFormat(combined), "");
        boolean noExplicitConstraints = !draft.constraints().isEmpty()
                ? draft.constraints().stream().anyMatch(this::isNoConstraintMarker)
                : indicatesNoExplicitConstraints(combined);
        List<String> constraints = !draft.constraints().isEmpty()
                ? draft.constraints()
                : noExplicitConstraints
                ? List.of("No explicit source, time range, or scope constraints were provided.")
                : List.of();
        return new KnownScopingState(outputFormat, constraints, noExplicitConstraints);
    }

    private String inferOutputFormat(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.toLowerCase();
        if (normalized.contains("full report")) {
            return "Full report";
        }
        if (normalized.contains("comparison") || normalized.contains("compare")) {
            return "Comparison";
        }
        if (normalized.contains("conclusion")) {
            return "Conclusion";
        }
        return "";
    }

    private boolean indicatesNoExplicitConstraints(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.toLowerCase();
        return normalized.contains("no constraints")
                || normalized.contains("no restriction")
                || normalized.contains("without constraints")
                || normalized.contains("no special requirements")
                || normalized.contains("no special constraints");
    }

    private boolean isNoConstraintMarker(String value) {
        return value != null && indicatesNoExplicitConstraints(value);
    }

    private List<String> filterMissingDecisionTypes(List<String> missingDecisionTypes, KnownScopingState knownState) {
        List<String> filtered = new ArrayList<>();
        for (String decisionType : missingDecisionTypes) {
            if (decisionType == null || decisionType.isBlank()) {
                continue;
            }
            String normalized = decisionType.trim().toLowerCase();
            if ("output_format".equals(normalized) && !knownState.outputFormat().isBlank()) {
                continue;
            }
            if (knownState.noExplicitConstraints()
                    && ("time_range".equals(normalized)
                    || "source_preference".equals(normalized)
                    || "region_focus".equals(normalized))) {
                continue;
            }
            if (!filtered.contains(normalized)) {
                filtered.add(normalized);
            }
        }
        return List.copyOf(filtered);
    }

    private List<String> fallbackClarificationQuestions(KnownScopingState knownState,
                                                        List<String> missingDecisionTypes) {
        List<String> questions = new ArrayList<>();
        for (String decisionType : missingDecisionTypes) {
            switch (decisionType) {
                case "output_format" -> {
                    if (knownState.outputFormat().isBlank()) {
                        questions.add("Do you want the final deliverable as a conclusion, a comparison, or a full report?");
                    }
                }
                case "scope_emphasis" ->
                        questions.add("Should the research lean more toward academic methods or engineering practice?");
                case "time_range" -> {
                    if (!knownState.noExplicitConstraints()) {
                        questions.add("Should I emphasize recent work or include the longer historical context as well?");
                    }
                }
                case "source_preference" -> {
                    if (!knownState.noExplicitConstraints()) {
                        questions.add("Should I prioritize academic papers, industry sources, or balance both?");
                    }
                }
                case "audience_depth" ->
                        questions.add("Should the report stay high level or go deeper into technical and mathematical details?");
                case "region_focus" -> {
                    if (!knownState.noExplicitConstraints()) {
                        questions.add("Do you want a global view or emphasis on a particular country or region?");
                    }
                }
                default -> {
                }
            }
            if (questions.size() >= 3) {
                break;
            }
        }
        return filterDuplicateQuestions(questions);
    }

    private List<String> filterDuplicateQuestions(List<String> questions) {
        List<String> deduplicated = new ArrayList<>();
        for (String question : questions) {
            if (question == null || question.isBlank()) {
                continue;
            }
            String normalized = normalizeQuestion(question);
            boolean exists = deduplicated.stream().anyMatch(existing -> normalizeQuestion(existing).equals(normalized));
            if (!exists) {
                deduplicated.add(question.trim());
            }
        }
        return List.copyOf(deduplicated);
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private List<String> mergeConstraints(List<String> preferred, List<String> fallback, List<String> lastFallback) {
        if (preferred != null && !preferred.isEmpty()) {
            return List.copyOf(preferred);
        }
        if (fallback != null && !fallback.isEmpty()) {
            return List.copyOf(fallback);
        }
        return lastFallback == null ? List.of() : List.copyOf(lastFallback);
    }

    private String mergeText(String preferred, String fallback, String lastFallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return lastFallback == null ? "" : lastFallback.trim();
    }

    private JsonNode parseJsonObject(String text) throws JsonProcessingException {
        String trimmed = text == null ? "" : text.trim();
        try {
            return objectMapper.readTree(trimmed);
        } catch (JsonProcessingException ignored) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return objectMapper.readTree(trimmed.substring(start, end + 1));
            }
            throw ignored;
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private MessageRecord persistMessage(String userId,
                                         String threadId,
                                         MessageRole role,
                                         String content,
                                         InteractionMode interactionMode,
                                         String runId,
                                         String taskId) {
        MessageRecord messageRecord = messageRepository.append(userId, new MessageRecord(
                UUID.randomUUID().toString(),
                threadId,
                role,
                content,
                interactionMode,
                runId,
                taskId,
                Instant.now()
        ));
        return messageRecord;
    }

    private void publishEvent(String userId,
                              String threadId,
                              String runId,
                              RunEventType runEventType,
                              Object payload,
                              Consumer<RunEvent> runEventConsumer) {
        RunEvent event = new RunEvent(runId, threadId, runEventType.value(), Instant.now(), payload);
        runEventRepository.appendEvent(userId, threadId, event);
        runEventConsumer.accept(event);
    }

    private void publishMemoryEvent(RunEventType eventType,
                                    String userId,
                                    String threadId,
                                    String taskId,
                                    String messageId) {
        memoryEventPublisher.publish(new MemoryEventPayload(
                eventType.value(),
                userId,
                threadId,
                taskId,
                messageId,
                Instant.now()
        ));
    }

    private String renderRecentConversation(ThreadMemoryView memoryView) {
        List<MessageRecord> messages = memoryView.recentMessages();
        if (messages.isEmpty()) {
            return "- none";
        }
        return messages.stream()
                .map(message -> "- " + message.role().name() + ": " + summarizeForContext(message.content()))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
    }

    private List<String> split(String content, int maxLength) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        String remaining = content;
        while (remaining.length() > maxLength) {
            segments.add(remaining.substring(0, maxLength));
            remaining = remaining.substring(maxLength);
        }
        if (!remaining.isBlank()) {
            segments.add(remaining);
        }
        return List.copyOf(segments);
    }

    private String textOrDefault(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node.path(fieldName);
        if (value.isTextual() && !value.asText().isBlank()) {
            return value.asText().trim();
        }
        return fallback;
    }

    private String summarizeForContext(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }

    private int nextRevision(ResearchDraftRecord currentDraft) {
        return currentDraft == null ? 1 : currentDraft.revision() + 1;
    }

    private List<String> readStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode value : node) {
                if (value.isTextual() && !value.asText().isBlank()) {
                    values.add(value.asText().trim());
                }
            }
        }
        return List.copyOf(values);
    }

    private List<ResearchPlanStep> readPlanSteps(JsonNode node, boolean hasDocuments) {
        List<ResearchPlanStep> steps = new ArrayList<>();
        if (node != null && node.isArray()) {
            int index = 1;
            for (JsonNode stepNode : node) {
                String title = textOrDefault(stepNode, "title", "");
                String objective = textOrDefault(stepNode, "objective", title);
                String query = textOrDefault(stepNode, "query", objective);
                String outputFocus = textOrDefault(stepNode, "outputFocus", "Key findings and evidence");
                if (title.isBlank()) {
                    continue;
                }
                steps.add(new ResearchPlanStep(
                        "step-" + index++,
                        title,
                        objective,
                        query.isBlank() ? title : query,
                        stepNode.path("useWeb").asBoolean(true),
                        stepNode.path("useDocuments").asBoolean(hasDocuments),
                        outputFocus
                ));
            }
        }
        return List.copyOf(steps);
    }

    private List<ResearchPlanStep> defaultPlanSteps(String latestUserInput, boolean hasDocuments) {
        String query = latestUserInput == null || latestUserInput.isBlank() ? "research topic" : latestUserInput.trim();
        return List.of(
                new ResearchPlanStep("step-1", "Define the problem and baseline context", "Clarify the topic, scope, and baseline facts", query, true, hasDocuments, "Definitions, scope, and baseline context"),
                new ResearchPlanStep("step-2", "Gather evidence and competing views", "Collect the strongest supporting evidence, disagreements, and caveats", query + " evidence competing views", true, hasDocuments, "Evidence, disagreements, and caveats"),
                new ResearchPlanStep("step-3", "Synthesize implications", "Summarize practical implications, limits, and unanswered questions", query + " implications limitations unanswered questions", true, hasDocuments, "Implications, limits, and unanswered questions")
        );
    }

    private String summarize(String content) {
        String normalized = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private void logFlow(java.util.function.Supplier<String> messageSupplier) {
        if (logAgentFlow && logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info(messageSupplier);
        }
    }

    public record ScopingResponse(
            String title,
            String brief,
            String objective,
            String scope,
            String outputFormat,
            List<String> constraints,
            String planSummary,
            List<ResearchPlanStep> planSteps,
            List<String> questions,
            boolean ready
    ) implements Serializable {
    }

    private record ScopingFrame(
            String title,
            String objective,
            String scope,
            String outputFormat,
            List<String> constraints,
            String researchUnderstanding,
            String planSummary,
            List<ResearchPlanStep> planSteps,
            List<String> missingDecisionTypes,
            boolean ready
    ) {
    }

    private record KnownScopingState(
            String outputFormat,
            List<String> constraints,
            boolean noExplicitConstraints
    ) {
    }
}
