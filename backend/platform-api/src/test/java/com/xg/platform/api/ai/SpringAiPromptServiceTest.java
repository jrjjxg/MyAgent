package com.xg.platform.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.AgentPromptRequest;
import com.xg.platform.conversation.domain.ConversationRouteKind;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.document.DocumentStatus;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.document.domain.DocumentChunk;
import com.xg.platform.document.domain.RetrievedChunk;
import com.xg.platform.skill.domain.SkillAvailabilityStatus;
import com.xg.platform.skill.domain.SkillDefinition;
import com.xg.platform.skill.domain.SkillExecutionMode;
import com.xg.platform.skill.domain.SkillInvocation;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolGroup;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiPromptServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final SpringAiPromptService promptService = new SpringAiPromptService();

    @Test
    void documentQaPromptUsesToolDrivenContractAndSelectedScope() {
        DocumentRecord document = document("doc-1", "stack-lstm.pdf", DocumentStatus.READY);
        AgentPromptRequest request = new AgentPromptRequest(
                "general-agent",
                "Summarize the paper",
                ConversationRouteKind.DOCUMENT_QA,
                List.of(),
                List.of(
                        docTool("inspect_document", "Inspect one document"),
                        docTool("search_document", "Search the current document scope"),
                        docTool("read_document", "Read a window of content")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(document),
                List.of(new RetrievedChunk(
                        new DocumentChunk("chunk-1", document.documentId(), document.name(), 1, 1, "Abstract", "Abstract", 1),
                        7
                )),
                "summary",
                "",
                "PLAN",
                List.of(),
                "Current understanding:\n- none",
                List.of(document.documentId()),
                "Reading plan:\n- Read Abstract first",
                List.of("Potential query term: innovation"),
                List.of(),
                List.of(),
                List.of()
        );

        String prompt = promptService.renderPrompt(request);

        assertThat(prompt).contains("tool-driven document question answering");
        assertThat(prompt).contains("Available document tools:");
        assertThat(prompt).contains("inspect_document");
        assertThat(prompt).contains("search_document");
        assertThat(prompt).contains("read_document");
        assertThat(prompt).contains("Current docs phase: PLAN");
        assertThat(prompt).contains("PLAN: build a quick reading plan");
        assertThat(prompt).contains("EXPLORE: use working memory and search hints");
        assertThat(prompt).contains("SYNTHESIZE: answer only when the scratchpad already contains enough direct evidence");
        assertThat(prompt).contains("read the abstract or introduction before broad searching");
        assertThat(prompt).contains("<document_reading_plan>");
        assertThat(prompt).contains("Read Abstract first");
        assertThat(prompt).contains("<document_search_hints>");
        assertThat(prompt).contains("Potential query term: innovation");
        assertThat(prompt).contains("Explicitly selected documents:");
        assertThat(prompt).contains("stack-lstm.pdf");
        assertThat(prompt).contains("Pending unsummarized history:");
        assertThat(prompt).contains("- none");
        assertThat(prompt).doesNotContain("Retrieved excerpts:");
        assertThat(prompt).contains("do not answer from filenames or generic background knowledge alone");
        assertThat(prompt).contains("[Document Name, p.X-Y]");
    }

    @Test
    void nonDocumentQaPromptStillIncludesRetrievedExcerpts() {
        DocumentRecord document = document("doc-2", "paper.pdf", DocumentStatus.READY);
        AgentPromptRequest request = new AgentPromptRequest(
                "general-agent",
                "Answer with evidence",
                ConversationRouteKind.CHAT,
                List.of(),
                List.of(
                        docTool("search_document", "Search the current document scope"),
                        docTool("read_document", "Read a window of content")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(document),
                List.of(new RetrievedChunk(
                        new DocumentChunk("chunk-2", document.documentId(), document.name(), 3, 4, "Evidence block", "Results", 2),
                        9
                )),
                "summary",
                "",
                "answer",
                List.of(),
                "",
                List.of(),
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        String prompt = promptService.renderPrompt(request);

        assertThat(prompt).contains("Retrieved excerpts:");
        assertThat(prompt).contains("[paper.pdf, p.3]");
        assertThat(prompt).contains("Available document tools:");
        assertThat(prompt).contains("search_document");
        assertThat(prompt).contains("read_document");
    }

    @Test
    void promptRendersAvailableSkillsCatalogWithLoadContract() {
        AgentPromptRequest request = new AgentPromptRequest(
                "general-agent",
                "Help me research this topic",
                ConversationRouteKind.CHAT,
                List.of(),
                List.of(
                        workspaceTool("load_skill", "Load a skill"),
                        workspaceTool("load_skill_resource", "Load a skill resource")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                "",
                "SYNTHESIZE",
                List.of(),
                "",
                List.of(),
                "",
                List.of(),
                List.of(skillDescriptor()),
                List.of(),
                List.of()
        );

        String prompt = promptService.renderPrompt(request);

        assertThat(prompt).contains("<available_skills>");
        assertThat(prompt).contains("<skillId>docs.paper-review</skillId>");
        assertThat(prompt).contains("<triggers>paper, review</triggers>");
        assertThat(prompt).contains("<preferredTools>load_skill</preferredTools>");
        assertThat(prompt).contains("<allowedTools>load_skill, load_skill_resource</allowedTools>");
        assertThat(prompt).contains("<requiresWeb>false</requiresWeb>");
        assertThat(prompt).contains("<requiresDocuments>false</requiresDocuments>");
        assertThat(prompt).contains("<invocation>manual</invocation>");
        assertThat(prompt).contains("<status>missing_env</status>");
        assertThat(prompt).contains("<statusReason>missing env: OPENAI_API_KEY</statusReason>");
        assertThat(prompt).contains("If exactly one skill clearly fits, call `load_skill` with the skillId first");
        assertThat(prompt).contains("call `load_skill_resource` with the declared resource path");
        assertThat(prompt).contains("<loaded_skills>");
        assertThat(prompt).doesNotContain("Recommended skills:");
        assertThat(prompt).doesNotContain("User-selected skills:");
    }

    @Test
    void promptMarksLoadedSkillsAsAlreadyActive() {
        AgentPromptRequest request = new AgentPromptRequest(
                "general-agent",
                "Help me research this topic",
                ConversationRouteKind.CHAT,
                List.of(),
                List.of(
                        workspaceTool("load_skill", "Load a skill"),
                        workspaceTool("load_skill_resource", "Load a skill resource")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                "",
                "SYNTHESIZE",
                List.of(),
                "",
                List.of(),
                "",
                List.of(),
                List.of(skillDescriptor()),
                List.of(),
                List.of(loadedSkillDefinition())
        );

        String prompt = promptService.renderPrompt(request);

        assertThat(prompt).contains("<loaded_skills>");
        assertThat(prompt).contains("<skillId>weather</skillId>");
        assertThat(prompt).contains("<preferredTools>weather</preferredTools>");
        assertThat(prompt).contains("<workflowSummary>");
        assertThat(prompt).contains("First use the `weather` tool with a clear location.");
        assertThat(prompt).contains("Do not call `load_skill` again for an already loaded skill");
    }

    private ToolDescriptor docTool(String name, String description) {
        return new ToolDescriptor(name, description, objectMapper.createObjectNode(), ToolGroup.DOCUMENTS, "builtin");
    }

    private ToolDescriptor workspaceTool(String name, String description) {
        return new ToolDescriptor(name, description, objectMapper.createObjectNode(), ToolGroup.WORKSPACE, "builtin");
    }

    private DocumentRecord document(String documentId, String name, DocumentStatus status) {
        Instant now = Instant.parse("2026-04-02T00:00:00Z");
        return new DocumentRecord(
                documentId,
                "workspace-1",
                "thread-1",
                "artifact-1",
                name,
                status,
                "text-1",
                "chunks-1",
                now,
                now
        );
    }

    private SkillDescriptor skillDescriptor() {
        return new SkillDescriptor(
                "docs.paper-review",
                "public:docs.paper-review",
                "Review a paper with grounded notes.",
                "Use this for structured paper analysis.",
                "https://example.com/docs.paper-review",
                "OPENAI_API_KEY",
                List.of("OPENAI_API_KEY"),
                List.of("paper", "review"),
                List.of("load_skill"),
                List.of("load_skill", "load_skill_resource"),
                List.of("references/checklist.md"),
                List.of("search"),
                List.of("summarize_paper"),
                false,
                false,
                "general-agent",
                "manual",
                "inline",
                false,
                "public",
                "d:/deepagents/myagent/skills/public/docs-paper-review/SKILL.md",
                "missing_env",
                "missing env: OPENAI_API_KEY"
        );
    }

    private SkillDefinition loadedSkillDefinition() {
        return new SkillDefinition(
                "weather",
                "public:weather",
                "Forecast skill.",
                "Use this for forecasts.",
                "https://example.com/weather",
                "",
                List.of(),
                List.of("weather"),
                List.of("weather"),
                List.of("weather"),
                List.of("references/forecast.md"),
                List.of(),
                List.of(),
                false,
                false,
                "general-agent",
                SkillInvocation.AUTO,
                SkillExecutionMode.INLINE,
                Path.of("d:/deepagents/myagent/skills/public/weather/SKILL.md"),
                """
                # Weather Skill

                Preferred workflow:
                - First use the `weather` tool with a clear location.
                - If the location is missing, ask for the city or region before continuing.
                - If the structured weather result is incomplete, only then fall back to `web_search` and `web_fetch`.
                """,
                true,
                "public",
                SkillAvailabilityStatus.READY,
                ""
        );
    }
}
