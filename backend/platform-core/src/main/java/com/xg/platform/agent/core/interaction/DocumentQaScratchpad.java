package com.xg.platform.agent.core.interaction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record DocumentQaScratchpad(
        DocumentQuestionType questionType,
        String leadDocumentName,
        List<String> understandingNotes,
        List<String> evidenceNotes,
        List<String> coveredSections,
        List<String> openQuestions,
        List<String> queriesTried,
        int weakSearchStreak
) implements Serializable {

    private static final int MAX_UNDERSTANDING_NOTES = 4;
    private static final int MAX_EVIDENCE_NOTES = 6;
    private static final int MAX_COVERED_SECTIONS = 8;
    private static final int MAX_OPEN_QUESTIONS = 4;
    private static final int MAX_QUERIES = 6;

    public DocumentQaScratchpad {
        questionType = questionType == null ? DocumentQuestionType.OVERVIEW : questionType;
        leadDocumentName = leadDocumentName == null ? "" : leadDocumentName;
        understandingNotes = immutableLimited(understandingNotes, MAX_UNDERSTANDING_NOTES);
        evidenceNotes = immutableLimited(evidenceNotes, MAX_EVIDENCE_NOTES);
        coveredSections = immutableLimited(coveredSections, MAX_COVERED_SECTIONS);
        openQuestions = immutableLimited(openQuestions, MAX_OPEN_QUESTIONS);
        queriesTried = immutableLimited(queriesTried, MAX_QUERIES);
        weakSearchStreak = Math.max(0, weakSearchStreak);
    }

    public static DocumentQaScratchpad initialize(DocumentQuestionType questionType,
                                                  String leadDocumentName,
                                                  List<String> openQuestions) {
        return new DocumentQaScratchpad(
                questionType,
                leadDocumentName,
                List.of(),
                List.of(),
                List.of(),
                openQuestions,
                List.of(),
                0
        );
    }

    public DocumentQaScratchpad withSearchObservation(String query,
                                                      List<String> nextOpenQuestions,
                                                      int nextWeakSearchStreak) {
        List<String> nextQueries = new ArrayList<>(queriesTried);
        if (query != null && !query.isBlank()) {
            nextQueries.add(query.trim());
        }
        return new DocumentQaScratchpad(
                questionType,
                leadDocumentName,
                understandingNotes,
                evidenceNotes,
                coveredSections,
                nextOpenQuestions,
                nextQueries,
                nextWeakSearchStreak
        );
    }

    public DocumentQaScratchpad withReadObservation(List<String> understandingAdditions,
                                                    List<String> evidenceAdditions,
                                                    List<String> coveredAdditions,
                                                    List<String> nextOpenQuestions) {
        List<String> nextUnderstanding = new ArrayList<>(understandingNotes);
        if (understandingAdditions != null) {
            nextUnderstanding.addAll(understandingAdditions);
        }
        List<String> nextEvidence = new ArrayList<>(evidenceNotes);
        if (evidenceAdditions != null) {
            nextEvidence.addAll(evidenceAdditions);
        }
        List<String> nextCovered = new ArrayList<>(coveredSections);
        if (coveredAdditions != null) {
            nextCovered.addAll(coveredAdditions);
        }
        return new DocumentQaScratchpad(
                questionType,
                leadDocumentName,
                nextUnderstanding,
                nextEvidence,
                nextCovered,
                nextOpenQuestions,
                queriesTried,
                0
        );
    }

    public boolean readyToSynthesize() {
        return evidenceNotes.size() >= minimumEvidenceRequired() && openQuestions.isEmpty();
    }

    public String render() {
        return """
                Current understanding:
                %s

                Evidence notes:
                %s

                Covered sections/pages:
                %s

                Open questions:
                %s

                Queries tried:
                %s
                """.formatted(
                renderList(understandingNotes),
                renderList(evidenceNotes),
                renderList(coveredSections),
                renderList(openQuestions),
                renderList(queriesTried)
        ).trim();
    }

    private int minimumEvidenceRequired() {
        return switch (questionType) {
            case SPECIFIC_FACT -> 1;
            case COMPARE -> 3;
            default -> 2;
        };
    }

    private static List<String> immutableLimited(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.trim();
            if (!normalized.isBlank()) {
                deduped.add(normalized);
            }
        }
        List<String> ordered = new ArrayList<>(deduped);
        if (ordered.size() > limit) {
            ordered = ordered.subList(ordered.size() - limit, ordered.size());
        }
        return List.copyOf(ordered);
    }

    private static String renderList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "- none";
        }
        return values.stream()
                .map(value -> "- " + value)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
    }
}
