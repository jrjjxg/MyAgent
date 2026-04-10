package com.xg.platform.api.ai;

import com.xg.platform.agent.core.AgentPromptRequest;
import com.xg.platform.agent.core.AgentPromptService;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.document.domain.RetrievedChunk;
import com.xg.platform.tooling.domain.ToolDescriptor;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpringAiPromptService implements AgentPromptService {

    @Override
    public String renderPrompt(AgentPromptRequest request) {
        List<String> sections = new ArrayList<>();
        sections.add(renderIdentitySection(request));
        sections.add(renderWorkflowOverlay(request));
        sections.add(renderClarificationPolicy());
        sections.add(renderConversationSection(request));
        sections.add(renderMemorySection(request));
        sections.add(renderSkillSection(request));
        sections.add(renderWorkspaceSection(request));
        sections.add(renderDocsSection(request));
        sections.add(renderCitationSection());
        sections.add(renderCurrentDateSection());
        String template = sections.stream()
                .filter(section -> section != null && !section.isBlank())
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        return new SystemPromptTemplate(template).render(Map.of());
    }

    private String renderIdentitySection(AgentPromptRequest request) {
        boolean hasWebSearch = hasTool(request, "web_search");
        boolean hasWebFetch = hasTool(request, "web_fetch");
        boolean hasSkillCommands = hasTool(request, "load_skill")
                || hasTool(request, "load_skill_resource")
                || hasTool(request, "run_skill_command")
                || hasTool(request, "skill_process_status")
                || hasTool(request, "stop_skill_process");
        String tools = request.availableTools().isEmpty()
                ? "- none"
                : request.availableTools().stream()
                .map(this::renderTool)
                .collect(Collectors.joining(System.lineSeparator()));
        List<String> toolPolicyLines = new ArrayList<>();
        toolPolicyLines.add("Available tools:");
        toolPolicyLines.add(tools);
        if (hasWebSearch || hasWebFetch) {
            toolPolicyLines.add("- Treat web_search as discovery and web_fetch as verification.");
            toolPolicyLines.add("- For real-time or web-backed factual answers, fetch at least one strong source before answering when possible.");
        }
        if (hasSkillCommands) {
            toolPolicyLines.add("- Skills and packaged commands are internal agent capabilities, not end-user terminal instructions.");
            toolPolicyLines.add("- Treat the skill directory as a catalog only. A listed skill is not active by default.");
            toolPolicyLines.add("- When one skill clearly matches the request, call `load_skill` before following that workflow.");
            toolPolicyLines.add("- Call `load_skill_resource` for bundled references or long guidance files.");
            toolPolicyLines.add("- Execute packaged commands via tools instead of telling the user to run shell commands.");
            toolPolicyLines.add("- Do not expose raw shell steps such as cd/bash/python/npm commands unless the user explicitly asks for terminal instructions.");
        }
        toolPolicyLines.add("- Do not narrate tool planning or analysis steps to the user; provide only the final user-facing answer.");
        return """
                <role>
                You are %s, a platform research agent for conversational document and web-assisted work.
                Your job is to plan carefully, explore evidence, synthesize grounded conclusions, and state limits honestly.
                </role>

                <workflow_policy>
                - Think concisely before acting.
                - Prefer grounded evidence over guesses.
                - When documents exist, explore evidence before concluding.
                - If lexical retrieval is weak, continue with broader exploration or lead-document evidence before giving up.
                - Best-effort answers are allowed when evidence is partial, but uncertainty must be explicit.
                - Do not invent sources, citations, or completed work.
                - Do not reveal internal reasoning, hidden analysis, or chain-of-thought to the user.
                </workflow_policy>

                <tool_use_policy>
                %s
                </tool_use_policy>
                """.formatted(request.agentName(), String.join(System.lineSeparator(), toolPolicyLines));
    }

    private String renderWorkflowOverlay(AgentPromptRequest request) {
        return switch (request.routeKind()) {
            case DOCUMENT_QA -> """
                    <execution_mode>
                    - This is tool-driven document question answering.
                    - Current docs phase: %s
                    - PLAN: build a quick reading plan and establish a basic understanding before answering.
                    - EXPLORE: use working memory and search hints to choose the next narrow query or reading step.
                    - SYNTHESIZE: answer only when the scratchpad already contains enough direct evidence.
                    - Use the document tools to inspect, search, and read the current document scope before concluding.
                    - Prefer direct document evidence over background knowledge.
                    - If evidence is missing, state that clearly instead of guessing from filenames or prior knowledge.
                    </execution_mode>
                    """.formatted(request.currentPhase() == null || request.currentPhase().isBlank() ? "PLAN" : request.currentPhase());
            default -> """
                    <execution_mode>
                    - This is chat.
                    - Answer directly when conversation context and stable knowledge are enough.
                    - Use tools when they materially improve correctness, freshness, grounding, or execution.
                    - Scan the available skill catalog before acting.
                    - If exactly one skill clearly fits, load it first and follow its workflow.
                    - If no skill clearly fits, continue with generic reasoning and generic tools.
                    - If a loaded skill stops fitting the task, fall back to generic tools instead of forcing the workflow.
                    - If thread documents look directly relevant, you may use document tools in chat mode before answering.
                    - If the task depends on current, ranked, comparative, recommendation, or externally verified information, prefer using the relevant tools instead of guessing.
                    - Load a skill only when its workflow would materially improve the answer.
                    - If the task would be materially better as a multi-step investigation, call `suggest_deep_research` with a reason and a concise suggested brief.
                    </execution_mode>
                    """;
        };
    }

    private String renderClarificationPolicy() {
        return """
                <clarification_policy>
                Clarify before acting when required information is missing for the selected workflow.
                For compare-papers requests, do not fake a comparison if fewer than two readable documents are available.
                </clarification_policy>
                """;
    }

    private String renderConversationSection(AgentPromptRequest request) {
        if (request.recentMessages() == null || request.recentMessages().isEmpty()) {
            return """
                    <conversation_context>
                    Recent conversation:
                    - none
                    </conversation_context>
                    """;
        }
        String transcript = request.recentMessages().stream()
                .map(this::renderMessage)
                .collect(Collectors.joining(System.lineSeparator()));
        return """
                <conversation_context>
                Recent conversation:
                %s
                </conversation_context>
                """.formatted(transcript);
    }

    private String renderMemorySection(AgentPromptRequest request) {
        String sessionSummary = request.sessionSummary() == null || request.sessionSummary().isBlank()
                ? "- none"
                : request.sessionSummary().trim();
        String pendingHistory = request.pendingHistoricalMessages() == null || request.pendingHistoricalMessages().isEmpty()
                ? "- none"
                : request.pendingHistoricalMessages().stream()
                .map(this::renderMessage)
                .collect(Collectors.joining(System.lineSeparator()));
        String longTermMemory = request.longTermMemory() == null || request.longTermMemory().isBlank()
                ? "- none"
                : request.longTermMemory().trim();
        return """
                <memory_context>
                Session summary:
                %s

                Pending unsummarized history:
                %s

                Long-term memory:
                %s
                </memory_context>
                """.formatted(sessionSummary, pendingHistory, longTermMemory);
    }

    private String renderSkillSection(AgentPromptRequest request) {
        String available = request.availableSkills().stream()
                .map(this::renderSkillDescriptor)
                .collect(Collectors.joining(System.lineSeparator()));
        if (available.isBlank()) {
            available = "<none />";
        }
        String loaded = request.loadedSkills().stream()
                .map(this::renderLoadedSkill)
                .collect(Collectors.joining(System.lineSeparator()));
        if (loaded.isBlank()) {
            loaded = "<none />";
        }
        return """
                <available_skills>
                %s

                Use the catalog to decide whether a skill is relevant.
                If exactly one skill clearly fits, call `load_skill` with the skillId first and follow that workflow.
                If several skills could fit, prefer the most specific one instead of loading many skills.
                If no skill clearly fits, skip skill loading and continue with generic tools.
                If the skill references bundled guides or policies, call `load_skill_resource` with the declared resource path.
                Avoid blocked skills unless the user explicitly wants them and you surface the limitation.
                Treat packaged commands as internal execution paths for the agent.
                </available_skills>

                <loaded_skills>
                %s

                Loaded skills are already active for this run.
                Do not call `load_skill` again for an already loaded skill unless the previous load failed or the user explicitly asks to reload it.
                Once a skill is loaded, prefer its workflow, preferred tools, resources, and package commands first.
                Loaded skills are guidance, not a lock. If a loaded skill stops fitting the task, fall back to generic tools.
                </loaded_skills>
                """.formatted(available, loaded);
    }

    private String renderWorkspaceSection(AgentPromptRequest request) {
        String uploads = request.uploadedFiles().isEmpty()
                ? "- none"
                : request.uploadedFiles().stream()
                .map(this::renderUpload)
                .collect(Collectors.joining(System.lineSeparator()));
        String actions = request.executedActions().isEmpty()
                ? "- none"
                : request.executedActions().stream()
                .map(action -> "- " + action)
                .collect(Collectors.joining(System.lineSeparator()));
        String workingMemory = request.workingMemory() == null || request.workingMemory().isBlank()
                ? "- none"
                : request.workingMemory().trim();
        return """
                <workspace_contract>
                User request:
                %s

                Uploaded files:
                %s

                Current phase: %s
                Executed actions:
                %s
                Working memory:
                %s
                </workspace_contract>
                """.formatted(request.message().trim(), uploads, request.currentPhase(), actions, workingMemory);
    }

    private String renderDocsSection(AgentPromptRequest request) {
        if (request.routeKind() != com.xg.platform.conversation.domain.ConversationRouteKind.DOCUMENT_QA) {
            String docTools = request.availableTools().stream()
                    .filter(tool -> tool.group() == com.xg.platform.tooling.domain.ToolGroup.DOCUMENTS)
                    .map(tool -> "- %s: %s".formatted(tool.name(), tool.description()))
                    .collect(Collectors.joining(System.lineSeparator()));
            if (docTools.isBlank()) {
                docTools = "- none";
            }
            String documents = request.documents().isEmpty()
                    ? "- none"
                    : request.documents().stream()
                    .map(this::renderDocument)
                    .collect(Collectors.joining(System.lineSeparator()));
            String chunks = request.retrievedChunks().isEmpty()
                    ? "- none"
                    : request.retrievedChunks().stream()
                    .map(this::renderChunk)
                    .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
            return """
                    <document_evidence_policy>
                    - Use retrieved excerpts first.
                    - If evidence is partial, say what it supports and what remains uncertain.
                    - When no direct citation exists for a claim, avoid making the claim as fact.
                    - In chat mode, if thread documents seem directly relevant, you may use document tools before answering.
                    </document_evidence_policy>

                    <document_research_context>
                    Available documents:
                    %s

                    Retrieved excerpts:
                    %s

                    Available document tools:
                    %s
                    </document_research_context>
                    """.formatted(documents, chunks, docTools);
        }
        String documents = request.documents().isEmpty()
                ? "- none"
                : request.documents().stream()
                .map(this::renderDocument)
                .collect(Collectors.joining(System.lineSeparator()));
        String selectedScope = request.selectedDocumentIds().isEmpty()
                ? "- no explicit document selection; you may use any listed workspace document"
                : request.documents().stream()
                .filter(document -> request.selectedDocumentIds().contains(document.documentId()))
                .map(document -> "- %s [%s]".formatted(document.name(), document.documentId()))
                .collect(Collectors.joining(System.lineSeparator()));
        if (selectedScope.isBlank()) {
            selectedScope = "- selected document ids were provided, but no matching documents are currently loaded";
        }
        String docTools = request.availableTools().stream()
                .filter(tool -> tool.group() == com.xg.platform.tooling.domain.ToolGroup.DOCUMENTS)
                .map(tool -> "- %s: %s".formatted(tool.name(), tool.description()))
                .collect(Collectors.joining(System.lineSeparator()));
        if (docTools.isBlank()) {
            docTools = "- none";
        }
        String readingPlan = request.readingPlan() == null || request.readingPlan().isBlank()
                ? "- none"
                : request.readingPlan().trim();
        String searchHints = request.searchHints() == null || request.searchHints().isEmpty()
                ? "- none"
                : request.searchHints().stream()
                .map(hint -> "- " + hint)
                .collect(Collectors.joining(System.lineSeparator()));
        return """
                <document_evidence_policy>
                - Treat document tools as the source of truth for this route.
                - If you do not know the structure, call `inspect_document` or `list_document_sections` first.
                - For overview, contribution, or summary questions, read the abstract or introduction before broad searching.
                - To locate an answer, call `search_document` before reading, and prefer narrow queries over broad ones.
                - If search results are partial, call `read_document` to inspect surrounding content.
                - Never claim to have read a document unless tool output supports it.
                - If a document is not READY, say it is still processing or failed instead of guessing.
                </document_evidence_policy>

                <document_scope>
                Explicitly selected documents:
                %s

                Available documents:
                %s
                </document_scope>

                <document_reading_plan>
                %s
                </document_reading_plan>

                <document_search_hints>
                %s
                </document_search_hints>

                <document_tools>
                Available document tools:
                %s
                </document_tools>
                """.formatted(selectedScope, documents, readingPlan, searchHints, docTools);
    }

    private String renderCitationSection() {
        return """
                <response_contract>
                - Answer in the user's language when practical.
                - Be direct and structured.
                - If clarification is required, ask the question and stop.
                - If evidence is limited, explain the limitation after attempting exploration.
                - Do not claim unread documents were fully analyzed if only partial evidence was available.
                - In document question answering, do not answer from filenames or generic background knowledge alone.
                </response_contract>

                <citation_contract>
                When using document evidence, cite it inline as [Document Name, p.X-Y] when possible.
                When using web search or fetched web evidence, cite it inline with a readable source marker such as [Source Title | domain].
                Do not present raw search-result snippets as confirmed evidence if no page was fetched.
                If web evidence materially supports the answer, include a short Sources section listing the URLs used.
                When evidence is partial, cite what exists and separate inference from quoted evidence.
                </citation_contract>
                """;
    }

    private String renderCurrentDateSection() {
        return "<current_date>%s</current_date>".formatted(LocalDate.now());
    }

    private String renderTool(ToolDescriptor tool) {
        return "- %s (%s/%s)".formatted(tool.name(), tool.group(), tool.source());
    }

    private boolean hasTool(AgentPromptRequest request, String toolName) {
        return request.availableTools().stream().anyMatch(tool -> tool.name().equals(toolName));
    }

    private String renderMessage(MessageRecord messageRecord) {
        return "- %s: %s".formatted(messageRecord.role().name(), truncate(messageRecord.content(), 300));
    }

    private String renderUpload(ThreadFileReference fileReference) {
        return "- %s (%s, %d bytes) at %s".formatted(
                fileReference.name(),
                fileReference.contentType(),
                fileReference.sizeBytes(),
                fileReference.absolutePath()
        );
    }

    private String renderDocument(DocumentRecord documentRecord) {
        return "- %s [%s]".formatted(documentRecord.name(), documentRecord.status());
    }

    private String renderChunk(RetrievedChunk retrievedChunk) {
        return """
                [%s, p.%d]
                %s
                """.formatted(
                retrievedChunk.chunk().documentName(),
                retrievedChunk.chunk().pageStart(),
                retrievedChunk.chunk().text()
        );
    }

    private String renderSkillDescriptor(SkillDescriptor skillDefinition) {
        String summary = skillDefinition.summary() == null ? "" : skillDefinition.summary().trim();
        return """
                <skill>
                <skillId>%s</skillId>
                <description>%s</description>
                <summary>%s</summary>
                <triggers>%s</triggers>
                <preferredTools>%s</preferredTools>
                <allowedTools>%s</allowedTools>
                <requiresWeb>%s</requiresWeb>
                <requiresDocuments>%s</requiresDocuments>
                <invocation>%s</invocation>
                <sourceKey>%s</sourceKey>
                <execution>%s</execution>
                <resources>%s</resources>
                <packageCommands>%s</packageCommands>
                <status>%s</status>
                <statusReason>%s</statusReason>
                <path>%s</path>
                </skill>
                """.formatted(
                escapeXml(skillDefinition.skillId()),
                escapeXml(skillDefinition.description()),
                escapeXml(summary),
                joinSkillValues(skillDefinition.triggers()),
                joinSkillValues(skillDefinition.preferredTools()),
                joinSkillValues(skillDefinition.allowedTools()),
                skillDefinition.requiresWeb(),
                skillDefinition.requiresDocuments(),
                escapeXml(skillDefinition.invocation()),
                escapeXml(skillDefinition.sourceKey()),
                escapeXml(skillDefinition.execution()),
                skillDefinition.resources().isEmpty() ? "none" : escapeXml(String.join(", ", skillDefinition.resources())),
                skillDefinition.packageCommands().isEmpty() ? "none" : escapeXml(String.join(", ", skillDefinition.packageCommands())),
                escapeXml(skillDefinition.status()),
                escapeXml(skillDefinition.statusReason() == null ? "" : skillDefinition.statusReason()),
                escapeXml(skillDefinition.path())
        );
    }

    private String renderLoadedSkill(com.xg.platform.skill.domain.SkillDefinition skillDefinition) {
        String summary = skillDefinition.summary() == null ? "" : skillDefinition.summary().trim();
        String packageCommands = skillDefinition.packageCommands().isEmpty()
                ? "none"
                : escapeXml(skillDefinition.packageCommands().stream()
                .map(com.xg.platform.skill.domain.SkillPackageCommand::commandId)
                .collect(Collectors.joining(", ")));
        String workflowSummary = summarizeWorkflow(skillDefinition);
        return """
                <skill>
                <skillId>%s</skillId>
                <summary>%s</summary>
                <preferredTools>%s</preferredTools>
                <workflowSummary>%s</workflowSummary>
                <resources>%s</resources>
                <packageCommands>%s</packageCommands>
                </skill>
                """.formatted(
                escapeXml(skillDefinition.skillId()),
                escapeXml(summary),
                joinSkillValues(skillDefinition.preferredTools()),
                escapeXml(workflowSummary),
                skillDefinition.resources().isEmpty() ? "none" : escapeXml(String.join(", ", skillDefinition.resources())),
                packageCommands
        );
    }

    private String summarizeWorkflow(com.xg.platform.skill.domain.SkillDefinition skillDefinition) {
        List<String> steps = new ArrayList<>();
        boolean inCodeFence = false;
        for (String rawLine : skillDefinition.body().split("\\R")) {
            String line = rawLine.trim();
            if (line.startsWith("```")) {
                inCodeFence = !inCodeFence;
                continue;
            }
            if (inCodeFence || line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String normalized = line
                    .replaceFirst("^[-*+]\\s+", "")
                    .replaceFirst("^\\d+\\.\\s+", "")
                    .trim();
            if (normalized.isBlank() || steps.contains(normalized)) {
                continue;
            }
            steps.add(normalized);
            if (steps.size() >= 6) {
                break;
            }
        }
        if (steps.isEmpty()) {
            return skillDefinition.summary() == null || skillDefinition.summary().isBlank()
                    ? "Use the loaded skill guidance before falling back to generic tools."
                    : skillDefinition.summary().trim();
        }
        return steps.stream()
                .map(step -> truncate(step, 140))
                .collect(Collectors.joining(" | "));
    }

    private String joinSkillValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return escapeXml(String.join(", ", values));
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private String escapeXml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
