package com.xg.platform.conversation.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentQueryPlannerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final DocumentQueryPlanner planner = new DocumentQueryPlanner();

    @Test
    void classifiesContributionQuestionAndBuildsAbstractFirstPlan() {
        DocumentQuestionType questionType = planner.classify("这篇论文的创新点和主要贡献是什么？");
        DocumentQaScratchpad scratchpad = planner.initializeScratchpad(
                "这篇论文的创新点和主要贡献是什么？",
                questionType,
                "stack-lstm.pdf"
        );

        String readingPlan = planner.buildReadingPlan(
                questionType,
                "stack-lstm.pdf",
                List.of("Abstract", "Introduction", "Method", "Results")
        );
        List<String> searchHints = planner.buildSearchHints(
                "这篇论文的创新点和主要贡献是什么？",
                scratchpad,
                List.of("Abstract", "Introduction", "Method", "Results")
        );

        assertThat(questionType).isEqualTo(DocumentQuestionType.CONTRIBUTION);
        assertThat(readingPlan).contains("Focus document: stack-lstm.pdf");
        assertThat(readingPlan).contains("Prioritize section: Abstract");
        assertThat(searchHints).anyMatch(hint -> hint.contains("Abstract and Introduction"));
        assertThat(searchHints).anyMatch(hint -> hint.contains("Potential query term"));
    }

    @Test
    void marksZeroHitSearchAsWeakAndSuggestsStructureRecovery() throws Exception {
        DocumentQaScratchpad scratchpad = planner.initializeScratchpad(
                "What is the main method?",
                DocumentQuestionType.METHOD,
                "paper.pdf"
        );
        var output = objectMapper.createObjectNode()
                .put("query", "stacked lstm method")
                .put("matchCount", 0)
                .put("note", "No matching excerpts were found.");

        DocumentQaScratchpad updated = planner.updateAfterSearch(
                "What is the main method?",
                scratchpad,
                output,
                List.of("Abstract", "Method", "Results")
        );

        assertThat(updated.weakSearchStreak()).isEqualTo(1);
        assertThat(updated.queriesTried()).contains("stacked lstm method");
        assertThat(updated.openQuestions()).anyMatch(question -> question.contains("Inspect document structure"));
    }

    @Test
    void recordsReadEvidenceAndSwitchesToSynthesizeWhenEnoughEvidenceExists() throws Exception {
        DocumentQaScratchpad scratchpad = planner.initializeScratchpad(
                "Summarize the paper",
                DocumentQuestionType.OVERVIEW,
                "paper.pdf"
        );
        var readOne = objectMapper.createObjectNode()
                .put("documentName", "paper.pdf")
                .put("pageStart", 1)
                .put("pageEnd", 1)
                .put("chunkStart", 1)
                .put("chunkEnd", 2)
                .put("content", "Abstract The paper proposes a stacked LSTM model for blind source separation.");
        var readTwo = objectMapper.createObjectNode()
                .put("documentName", "paper.pdf")
                .put("pageStart", 5)
                .put("pageEnd", 6)
                .put("chunkStart", 8)
                .put("chunkEnd", 10)
                .put("content", "Results The experiments show the proposed method improves SDR compared with baselines.");

        DocumentQaScratchpad afterFirstRead = planner.updateAfterRead("Summarize the paper", scratchpad, readOne);
        DocumentQaScratchpad afterSecondRead = planner.updateAfterRead("Summarize the paper", afterFirstRead, readTwo);

        assertThat(afterFirstRead.evidenceNotes()).hasSize(1);
        assertThat(afterFirstRead.coveredSections()).singleElement()
                .satisfies(value -> assertThat(value).contains("chunks 1-2"));
        assertThat(afterSecondRead.evidenceNotes()).hasSize(2);
        assertThat(afterSecondRead.readyToSynthesize()).isTrue();
        assertThat(planner.determinePhase("EXPLORE", "read_document", afterSecondRead)).isEqualTo("SYNTHESIZE");
    }
}
