package com.xg.platform.conversation.runtime;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class DocumentQueryPlanner {

    public DocumentQuestionType classify(String question) {
        String normalized = normalize(question);
        if (containsAny(normalized, "compare", "comparison", "versus", "vs", "区别", "对比", "比较")) {
            return DocumentQuestionType.COMPARE;
        }
        if (containsAny(normalized, "limitation", "limitations", "future work", "weakness", "caveat", "不足", "局限", "未来工作")) {
            return DocumentQuestionType.LIMITATION;
        }
        if (containsAny(normalized, "method", "methods", "architecture", "model", "network", "framework", "算法", "方法", "模型", "结构")) {
            return DocumentQuestionType.METHOD;
        }
        if (containsAny(normalized, "result", "results", "experiment", "performance", "ablation", "metric", "实验", "结果", "性能", "指标", "提升")) {
            return DocumentQuestionType.RESULT;
        }
        if (containsAny(normalized, "contribution", "contributions", "innovation", "innovative", "novel", "main idea", "创新", "贡献", "亮点")) {
            return DocumentQuestionType.CONTRIBUTION;
        }
        if (containsAny(normalized, "summary", "summarize", "overview", "what is this paper about", "概述", "总结", "主要内容")) {
            return DocumentQuestionType.OVERVIEW;
        }
        if (containsAny(normalized, "what", "which", "who", "when", "where", "how many", "多少", "哪个", "是什么", "是否")) {
            return DocumentQuestionType.SPECIFIC_FACT;
        }
        return DocumentQuestionType.OVERVIEW;
    }

    public String buildReadingPlan(DocumentQuestionType questionType,
                                   String leadDocumentName,
                                   List<String> sectionTitles) {
        List<String> sectionHints = prioritizedSections(questionType, sectionTitles);
        String sectionSummary = sectionHints.isEmpty()
                ? "- Use inspect_document or list_document_sections to recover structure before deeper reading."
                : sectionHints.stream()
                .map(section -> "- Prioritize section: " + section)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
        return """
                Focus document: %s
                Reading plan:
                - Establish a quick mental model of the paper before answering.
                %s
                - Read only enough surrounding text to support the answer with direct evidence.
                - Prefer moving from locate -> read -> note -> decide, instead of repeatedly broad searching.
                """.formatted(
                leadDocumentName == null || leadDocumentName.isBlank() ? "current document scope" : leadDocumentName,
                sectionSummary
        ).trim();
    }

    public List<String> buildSearchHints(String question,
                                         DocumentQaScratchpad scratchpad,
                                         List<String> sectionTitles) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        DocumentQuestionType questionType = scratchpad == null ? classify(question) : scratchpad.questionType();
        switch (questionType) {
            case CONTRIBUTION, OVERVIEW -> {
                hints.add("Start from Abstract and Introduction before broader search.");
                hints.add("Look for explicit contribution, innovation, or summary statements.");
                hints.add("After the high-level read, verify one method detail and one result claim.");
            }
            case METHOD -> {
                hints.add("Use section-aware queries around method, model, architecture, or framework.");
                hints.add("After finding the method section, read enough surrounding text to capture inputs, blocks, and training setup.");
            }
            case RESULT -> {
                hints.add("Search for experiment, results, evaluation, ablation, baseline, metric, or robustness evidence.");
                hints.add("Prefer narrow metric-oriented searches over broad summary terms.");
            }
            case LIMITATION -> {
                hints.add("Inspect conclusion, discussion, limitations, and future work sections.");
                hints.add("If the paper does not state limitations directly, look for caveats in experiments or discussion.");
            }
            case COMPARE -> {
                hints.add("First confirm the comparable documents and matching sections.");
                hints.add("Collect separate method and result evidence before comparing.");
            }
            case SPECIFIC_FACT -> {
                hints.add("Use the question terms directly in a narrow search query.");
                hints.add("If lexical search is weak, inspect structure and switch to section-driven reading.");
            }
        }
        for (String keyword : questionKeywords(question)) {
            hints.add("Potential query term: " + keyword);
        }
        if (scratchpad != null && scratchpad.weakSearchStreak() >= 2 && !sectionTitles.isEmpty()) {
            hints.add("Lexical search is weak; pivot to section-title-driven exploration.");
            sectionTitles.stream().limit(4).forEach(section -> hints.add("Candidate section: " + section));
        } else {
            prioritizedSections(questionType, sectionTitles).stream()
                    .limit(3)
                    .forEach(section -> hints.add("Relevant section: " + section));
        }
        return List.copyOf(hints);
    }

    public DocumentQaScratchpad initializeScratchpad(String question,
                                                     DocumentQuestionType questionType,
                                                     String leadDocumentName) {
        return DocumentQaScratchpad.initialize(questionType, leadDocumentName, initialOpenQuestions(questionType));
    }

    public DocumentQaScratchpad updateAfterSearch(String question,
                                                  DocumentQaScratchpad scratchpad,
                                                  JsonNode output,
                                                  List<String> sectionTitles) {
        String query = output == null ? "" : output.path("query").asText("");
        int matchCount = output == null ? 0 : output.path("matchCount").asInt(0);
        int topScore = 0;
        if (output != null && output.path("matches").isArray() && output.path("matches").size() > 0) {
            topScore = output.path("matches").get(0).path("score").asInt(0);
        }
        if (matchCount == 0) {
            return scratchpad.withSearchObservation(
                    query,
                    List.of("No direct matches yet. Inspect document structure or section titles before searching again."),
                    scratchpad.weakSearchStreak() + 1
            );
        }
        if (topScore <= 3 || matchCount == 1) {
            List<String> questions = new ArrayList<>();
            questions.add("Search results are weak. Read the top hit, then pivot using section titles if needed.");
            prioritizedSections(scratchpad.questionType(), sectionTitles).stream()
                    .limit(2)
                    .forEach(section -> questions.add("Consider reading section: " + section));
            return scratchpad.withSearchObservation(query, questions, scratchpad.weakSearchStreak() + 1);
        }
        if (scratchpad.questionType() == DocumentQuestionType.SPECIFIC_FACT) {
            return scratchpad.withSearchObservation(query, List.of(), 0);
        }
        return scratchpad.withSearchObservation(
                query,
                List.of("Read the strongest matched pages and record direct evidence before answering."),
                0
        );
    }

    public DocumentQaScratchpad updateAfterRead(String question,
                                                DocumentQaScratchpad scratchpad,
                                                JsonNode output) {
        String documentName = output == null ? scratchpad.leadDocumentName() : output.path("documentName").asText(scratchpad.leadDocumentName());
        int pageStart = output == null ? 1 : output.path("pageStart").asInt(1);
        int pageEnd = output == null ? pageStart : output.path("pageEnd").asInt(pageStart);
        String content = output == null ? "" : output.path("content").asText("");
        String pageLabel = "[%s, p.%d%s]".formatted(
                documentName == null || documentName.isBlank() ? "Document" : documentName,
                pageStart,
                pageEnd > pageStart ? "-" + pageEnd : ""
        );
        String summary = summarizeContent(content);
        List<String> understandingAdditions = List.of(
                "Read %s and extracted a focused summary: %s".formatted(pageLabel, summary)
        );
        List<String> evidenceAdditions = List.of(pageLabel + " " + summary);
        List<String> coveredAdditions = List.of(
                "%s (chunks %d-%d)".formatted(
                        pageLabel,
                        output == null ? 1 : output.path("chunkStart").asInt(1),
                        output == null ? 1 : output.path("chunkEnd").asInt(1)
                )
        );
        List<String> nextOpenQuestions = nextOpenQuestionsAfterRead(scratchpad);
        return scratchpad.withReadObservation(understandingAdditions, evidenceAdditions, coveredAdditions, nextOpenQuestions);
    }

    public String determinePhase(String currentPhase,
                                 String lastToolName,
                                 DocumentQaScratchpad scratchpad) {
        if (scratchpad.readyToSynthesize()) {
            return "SYNTHESIZE";
        }
        if ("search_document".equals(lastToolName) || "read_document".equals(lastToolName)) {
            return "EXPLORE";
        }
        if (currentPhase == null || currentPhase.isBlank()) {
            return "PLAN";
        }
        return currentPhase;
    }

    public List<String> knownSectionTitles(List<String> sectionTitles) {
        if (sectionTitles == null || sectionTitles.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String sectionTitle : sectionTitles) {
            if (sectionTitle == null) {
                continue;
            }
            String normalized = sectionTitle.trim();
            if (!normalized.isBlank()) {
                deduped.add(normalized);
            }
        }
        return List.copyOf(deduped);
    }

    private List<String> initialOpenQuestions(DocumentQuestionType questionType) {
        return switch (questionType) {
            case CONTRIBUTION, OVERVIEW -> List.of(
                    "Read the abstract or introduction to understand the paper goal.",
                    "Find explicit contribution or innovation statements.",
                    "Verify at least one supporting method or result claim."
            );
            case METHOD -> List.of(
                    "Locate the method or model section.",
                    "Capture the main architecture or training idea.",
                    "Find supporting setup details if they matter to the answer."
            );
            case RESULT -> List.of(
                    "Locate experiment or result evidence.",
                    "Record concrete metrics, comparisons, or robustness claims."
            );
            case LIMITATION -> List.of(
                    "Check conclusion or discussion for limitations and future work.",
                    "Look for caveats or weaknesses in experimental evidence."
            );
            case COMPARE -> List.of(
                    "Confirm the documents and sections being compared.",
                    "Collect method evidence before comparing.",
                    "Collect result evidence before comparing."
            );
            case SPECIFIC_FACT -> List.of(
                    "Find the exact passage that answers the question."
            );
        };
    }

    private List<String> nextOpenQuestionsAfterRead(DocumentQaScratchpad scratchpad) {
        int nextEvidenceCount = scratchpad.evidenceNotes().size() + 1;
        int requiredEvidence = scratchpad.questionType() == DocumentQuestionType.SPECIFIC_FACT
                ? 1
                : scratchpad.questionType() == DocumentQuestionType.COMPARE ? 3 : 2;
        if (nextEvidenceCount >= requiredEvidence) {
            return List.of();
        }
        return switch (scratchpad.questionType()) {
            case CONTRIBUTION, OVERVIEW -> List.of(
                    "Find one more method or result passage before answering."
            );
            case METHOD -> List.of(
                    "Find one more supporting passage about architecture, inputs, or training details."
            );
            case RESULT -> List.of(
                    "Find one more explicit metric, baseline, or conclusion passage."
            );
            case LIMITATION -> List.of(
                    "Find one more limitation, caveat, or future-work passage."
            );
            case COMPARE -> List.of(
                    "Collect additional evidence from the remaining document or section before comparing."
            );
            case SPECIFIC_FACT -> List.of();
        };
    }

    private List<String> prioritizedSections(DocumentQuestionType questionType, List<String> sectionTitles) {
        List<String> normalizedSections = knownSectionTitles(sectionTitles);
        List<String> priorities = switch (questionType) {
            case CONTRIBUTION, OVERVIEW -> List.of("Abstract", "Introduction", "Conclusion", "Discussion", "Results");
            case METHOD -> List.of("Method", "Methods", "Proposed Method", "Architecture", "Model", "Approach", "Experimental Setup");
            case RESULT -> List.of("Results", "Experiments", "Evaluation", "Ablation", "Conclusion");
            case LIMITATION -> List.of("Discussion", "Conclusion", "Limitations", "Future Work", "Results");
            case COMPARE -> List.of("Method", "Results", "Experiments", "Evaluation", "Conclusion");
            case SPECIFIC_FACT -> List.of("Abstract", "Introduction", "Method", "Results", "Conclusion");
        };
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String priority : priorities) {
            normalizedSections.stream()
                    .filter(section -> section.toLowerCase(Locale.ROOT).contains(priority.toLowerCase(Locale.ROOT)))
                    .forEach(ordered::add);
        }
        if (ordered.isEmpty()) {
            ordered.addAll(normalizedSections.stream().limit(5).toList());
        }
        return List.copyOf(ordered);
    }

    private List<String> questionKeywords(String question) {
        String normalized = normalize(question);
        if (normalized.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String token : normalized.split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (token.isBlank() || token.length() < 2) {
                continue;
            }
            keywords.add(token);
            if (keywords.size() >= 6) {
                break;
            }
        }
        return List.copyOf(keywords);
    }

    private boolean containsAny(String value, String... candidates) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String summarizeContent(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "No readable content was returned.";
        }
        if (normalized.length() > 260) {
            normalized = normalized.substring(0, 260) + "...";
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
