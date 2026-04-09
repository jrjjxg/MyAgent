package com.xg.platform.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentGraphToolCall;
import com.xg.platform.agent.core.ExecutionSource;
import com.xg.platform.agent.core.ResearchPlan;
import com.xg.platform.agent.core.ResearchUnit;
import com.xg.platform.agent.core.ToolUseLimits;
import com.xg.platform.agent.core.chat.ChatRouteKind;
import com.xg.platform.agent.core.interaction.DocumentQaScratchpad;
import com.xg.platform.agent.core.interaction.DocumentQuestionType;
import com.xg.platform.agent.core.research.execution.ResearchSessionState;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.document.DocumentStatus;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.message.ApprovedResearchPlan;
import com.xg.platform.contracts.message.InteractionMode;
import com.xg.platform.contracts.message.MessageRecord;
import com.xg.platform.contracts.message.MessageRole;
import com.xg.platform.contracts.message.PostMessageRequest;
import com.xg.platform.contracts.message.ResearchPlanStep;
import com.xg.platform.contracts.research.ResearchAgendaItem;
import com.xg.platform.contracts.research.ResearchEvidenceStatus;
import com.xg.platform.contracts.research.ResearchFindingRecord;
import com.xg.platform.contracts.research.ResearchGapRecord;
import com.xg.platform.contracts.research.ResearchIterationRecord;
import com.xg.platform.contracts.research.ResearchQueryRecord;
import com.xg.platform.contracts.research.ResearchReportSection;
import com.xg.platform.contracts.research.ResearchSourceKind;
import com.xg.platform.contracts.research.ResearchSourceRecord;
import com.xg.platform.contracts.skill.SkillDescriptor;
import com.xg.platform.tools.ToolDescriptor;
import com.xg.platform.tools.ToolGroup;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.serializer.Serializer.readUTF;

class PlatformJacksonStateSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripsInteractionStateWithTypedValuesAsJson() throws Exception {
        PlatformJacksonStateSerializer<InteractionState> serializer = new PlatformJacksonStateSerializer<>(InteractionState::new, objectMapper);
        ToolUseLimits toolUseLimits = new ToolUseLimits(6, 4, 2, 2, 1, 30_000L);
        byte[] thoughtSignature = new byte[]{1, 2, 3};
        assertThat(toolUseLimits.tryAcquire("web_search")).isTrue();
        assertThat(toolUseLimits.tryAcquire("web_fetch")).isTrue();

        Map<String, Object> data = Map.ofEntries(
                Map.entry(InteractionState.REQUEST, new PostMessageRequest("hello", InteractionMode.CHAT, "gemini", List.of("image-1"), List.of("doc-1"))),
                Map.entry(InteractionState.ROUTE_KIND, ChatRouteKind.DOCUMENT_QA),
                Map.entry(InteractionState.AVAILABLE_SKILLS, List.of(skillDescriptor())),
                Map.entry(InteractionState.AVAILABLE_TOOLS, List.of(toolDescriptor())),
                Map.entry(InteractionState.MESSAGES, List.of(AgentGraphMessage.assistant(
                        "assistant-1",
                        "Searching",
                        List.of(new AgentGraphToolCall("call-1", "search_document", objectMapper.createObjectNode().put("query", "transformer"))),
                        Map.of("thoughtSignatures", List.of(thoughtSignature))
                ))),
                Map.entry(InteractionState.TOOL_USE_LIMITS, toolUseLimits),
                Map.entry(InteractionState.USER_MESSAGE, messageRecord("message-1", MessageRole.USER, "hello")),
                Map.entry(InteractionState.MEMORY_VIEW, new ThreadMemoryView(
                        "thread-1",
                        "summary",
                        List.of(messageRecord("message-0", MessageRole.USER, "earlier")),
                        List.of(),
                        "draft-1",
                        "task-1",
                        "planning"
                )),
                Map.entry(InteractionState.DOCUMENT_SCRATCHPAD, DocumentQaScratchpad.initialize(
                        DocumentQuestionType.OVERVIEW,
                        "paper.pdf",
                        List.of("Read abstract")
                )),
                Map.entry(InteractionState.AVAILABLE_DOCUMENTS, List.of(new DocumentRecord(
                        "doc-1",
                        "workspace-1",
                        "thread-1",
                        "artifact-1",
                        "paper.pdf",
                        DocumentStatus.READY,
                        "artifact-text-1",
                        "artifact-index-1",
                        Instant.parse("2026-04-08T10:00:00Z"),
                        Instant.parse("2026-04-08T10:05:00Z")
                ))),
                Map.entry(InteractionState.SOURCES, List.of(new ExecutionSource(
                        "WEB_PAGE",
                        "Example",
                        "example.com",
                        "https://example.com",
                        true,
                        true
                )))
        );

        String json = serializer.writeDataAsString(data);
        InteractionState restored = serializer.bytesToObject(serializer.objectToBytes(new InteractionState(data)));

        assertThat(json).contains("\"availableSkills\"");
        assertThat(json).contains(PlatformJacksonStateSerializer.TYPE_PROPERTY);
        assertThat(restored.routeKind()).contains(ChatRouteKind.DOCUMENT_QA);
        assertThat(restored.<PostMessageRequest>request()).contains(new PostMessageRequest("hello", InteractionMode.CHAT, "gemini", List.of("image-1"), List.of("doc-1")));
        assertThat(restored.<SkillDescriptor>availableSkills()).singleElement().isEqualTo(skillDescriptor());
        assertThat(restored.<ToolDescriptor>availableTools()).singleElement().extracting(ToolDescriptor::name).isEqualTo("search_document");
        assertThat(restored.messages()).singleElement().extracting(AgentGraphMessage::messageId).isEqualTo("assistant-1");
        assertThat(restored.messages().get(0).messageProperties()).containsKey("thoughtSignatures");
        List<byte[]> restoredThoughtSignatures = (List<byte[]>) restored.messages().get(0).messageProperties().get("thoughtSignatures");
        assertThat(restoredThoughtSignatures).hasSize(1);
        assertThat(restoredThoughtSignatures.get(0)).containsExactly(thoughtSignature);
        assertThat(restored.<ToolUseLimits>toolUseLimits()).isPresent();
        ToolUseLimits restoredLimits = restored.<ToolUseLimits>toolUseLimits().orElseThrow();
        assertThat(restoredLimits.totalCalls()).isEqualTo(2);
        assertThat(restoredLimits.searchCalls()).isEqualTo(1);
        assertThat(restoredLimits.fetchCalls()).isEqualTo(1);
        assertThat(restored.<DocumentQaScratchpad>documentScratchpad()).contains(DocumentQaScratchpad.initialize(
                DocumentQuestionType.OVERVIEW,
                "paper.pdf",
                List.of("Read abstract")
        ));
        assertThat(restored.<DocumentRecord>availableDocuments()).singleElement().extracting(DocumentRecord::documentId).isEqualTo("doc-1");
        assertThat(restored.<ExecutionSource>sources()).singleElement().extracting(ExecutionSource::url).isEqualTo("https://example.com");

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializer.dataToBytes(data)))) {
            String storedJson = readUTF(input);
            assertThat(storedJson).contains("\"routeKind\"");
            assertThat(storedJson).contains("DOCUMENT_QA");
        }
    }

    @Test
    void roundTripsResearchStateWithPlanAndSession() throws Exception {
        PlatformJacksonStateSerializer<ResearchTaskState> serializer = new PlatformJacksonStateSerializer<>(ResearchTaskState::new, objectMapper);
        ResearchSessionState session = new ResearchSessionState(
                "research brief",
                List.of(new ResearchAgendaItem("agenda-1", "Overview", "Understand the market", "high", "Cover the market baseline", false)),
                List.of(new ResearchQueryRecord("query-1", 1, "discovery_search", "AI chips market size", "broad", "useful", 5, 2)),
                List.of(new ResearchSourceRecord("source-1", ResearchSourceKind.WEB_PAGE, "Example", "https://example.com", null, "snippet", "example.com", null, null, 1, "AI chips market size", ResearchEvidenceStatus.VERIFIED.name().toLowerCase(), "web_fetch", List.of(), List.of(), "anchor")),
                List.of(new ResearchFindingRecord("finding-1", "Demand is rising", "Usage is increasing", "medium", "", List.of("source-1"), false, null)),
                List.of(new ResearchGapRecord("gap-1", 1, "China", "Need regional detail", "Search regional market sources", false)),
                List.of(new ResearchIterationRecord(1, "intermediate_synthesis", "Partial synthesis", List.of("Demand is rising"), List.of("What is regional split?"), List.of("Search China market"), List.of("query-1"), List.of("source-1"))),
                List.of(new ResearchReportSection("section-1", "Executive Summary", "Summarize top findings")),
                List.of("AI chips China market"),
                1,
                "gap_analysis",
                false,
                "",
                Instant.parse("2026-04-08T09:00:00Z"),
                "",
                "",
                8,
                600_000L
        );

        Map<String, Object> data = Map.of(
                ResearchTaskState.APPROVED_PLAN, new ApprovedResearchPlan(
                        "draft-1",
                        1,
                        "AI chips",
                        "Research the market",
                        "Understand demand",
                        "2025-2026",
                        "report",
                        List.of("Use public sources"),
                        "Cover baseline and competitive dynamics",
                        List.of(new ResearchPlanStep("step-1", "Baseline", "Understand market size", "AI chips market size", true, true, "Market size"))
                ),
                ResearchTaskState.RESEARCH_PLAN, new ResearchPlan(
                        "Cover baseline and competitive dynamics",
                        List.of(new ResearchUnit("unit-1", "Baseline", "Understand market size", "AI chips market size", true, true, "Market size"))
                ),
                ResearchTaskState.RESEARCH_SESSION, session,
                ResearchTaskState.PLAN, session.reportPlan(),
                ResearchTaskState.FINDINGS, session.findingLedger(),
                ResearchTaskState.SOURCES, session.sourceLedger(),
                ResearchTaskState.ITERATIONS, session.iterationNotes(),
                ResearchTaskState.CURRENT_STAGE, "gap_analysis"
        );

        ResearchTaskState restored = serializer.bytesToObject(serializer.objectToBytes(new ResearchTaskState(data)));

        assertThat(restored.<ApprovedResearchPlan>approvedPlan()).isPresent();
        assertThat(restored.<ResearchPlan>researchPlan()).isPresent();
        assertThat(restored.<ResearchSessionState>researchSession()).contains(session);
        assertThat(restored.<ResearchReportSection>plan()).singleElement().extracting(ResearchReportSection::title).isEqualTo("Executive Summary");
        assertThat(restored.<List<ResearchFindingRecord>>findings().orElseThrow()).singleElement().extracting(ResearchFindingRecord::findingId).isEqualTo("finding-1");
        assertThat(restored.<ResearchSourceRecord>sources()).singleElement().extracting(ResearchSourceRecord::sourceId).isEqualTo("source-1");
        assertThat(restored.currentStage()).contains("gap_analysis");
    }

    private SkillDescriptor skillDescriptor() {
        return new SkillDescriptor(
                "skill-1",
                "local/skill-1",
                "Skill description",
                "Skill summary",
                "https://example.com/skill-1",
                "node",
                List.of("API_KEY"),
                List.of("skill trigger"),
                List.of("shell_command"),
                List.of("shell_command", "read_file"),
                List.of("references/guide.md"),
                List.of("docs"),
                List.of("npm run skill"),
                true,
                false,
                "default",
                "load_skill",
                "subagent",
                true,
                "workspace",
                "/skills/skill-1",
                "ready",
                ""
        );
    }

    private ToolDescriptor toolDescriptor() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        return new ToolDescriptor("search_document", "Search documents", schema, ToolGroup.DOCUMENTS, "builtin");
    }

    private MessageRecord messageRecord(String messageId,
                                        MessageRole role,
                                        String content) {
        return new MessageRecord(
                messageId,
                "thread-1",
                role,
                content,
                InteractionMode.CHAT,
                "run-1",
                null,
                Instant.parse("2026-04-08T10:00:00Z")
        );
    }
}
