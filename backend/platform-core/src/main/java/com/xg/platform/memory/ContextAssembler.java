package com.xg.platform.memory;

import com.xg.platform.contracts.document.DocumentRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ContextAssembler {

    public List<RetrievedChunk> retrieve(String query,
                                         List<DocumentRecord> documents,
                                         java.util.function.Function<DocumentRecord, List<DocumentChunk>> chunkLoader,
                                         int limit) {
        String normalizedQuery = normalize(query);
        List<String> queryTerms = tokenize(query);
        List<RetrievedChunk> matches = new ArrayList<>();
        for (DocumentRecord document : documents) {
            for (DocumentChunk chunk : chunkLoader.apply(document)) {
                int score = score(normalizedQuery, queryTerms, document.name(), chunk.sectionTitle(), chunk.searchableText());
                if (score <= 0) {
                    continue;
                }
                matches.add(new RetrievedChunk(chunk, score));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(RetrievedChunk::score).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private int score(String normalizedQuery,
                      List<String> queryTerms,
                      String documentName,
                      String sectionTitle,
                      String text) {
        String normalizedDocName = normalize(documentName);
        String normalizedSectionTitle = normalize(sectionTitle);
        String normalizedText = normalize(text);
        int score = 0;
        if (!normalizedQuery.isBlank()) {
            if (normalizedDocName.contains(normalizedQuery)) {
                score += 10;
            }
            if (normalizedSectionTitle.contains(normalizedQuery)) {
                score += 12;
            }
            if (normalizedText.contains(normalizedQuery)) {
                score += 8;
            }
        }
        for (String term : queryTerms) {
            if (term.length() < 2) {
                continue;
            }
            score += Math.min(3, countOccurrences(normalizedDocName, term)) * 5;
            score += Math.min(3, countOccurrences(normalizedSectionTitle, term)) * 4;
            score += Math.min(4, countOccurrences(normalizedText, term)) * 2;
        }
        for (String phrase : phrases(queryTerms)) {
            if (phrase.length() < 4) {
                continue;
            }
            if (normalizedSectionTitle.contains(phrase)) {
                score += 6;
            }
            if (normalizedText.contains(phrase)) {
                score += 4;
            }
        }
        return score;
    }

    private List<String> tokenize(String value) {
        return new ArrayList<>(java.util.Arrays.stream(normalize(value).split("[^a-z0-9\\u4e00-\\u9fa5]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private List<String> phrases(List<String> queryTerms) {
        List<String> phrases = new ArrayList<>();
        for (int index = 0; index < queryTerms.size() - 1; index++) {
            phrases.add(queryTerms.get(index) + " " + queryTerms.get(index + 1));
        }
        return phrases;
    }

    private int countOccurrences(String text, String term) {
        if (text == null || text.isBlank() || term == null || term.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(term, index)) >= 0) {
            count++;
            index += term.length();
        }
        return count;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
