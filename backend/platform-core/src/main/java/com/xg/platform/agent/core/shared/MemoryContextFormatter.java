package com.xg.platform.agent.core.shared;

import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MemoryContextFormatter {

    public String formatLongTermMemory(List<LongTermMemoryRecord> memories) {
        return formatLongTermMemory(memories, null);
    }

    public String formatLongTermMemory(List<LongTermMemoryRecord> memories, String threadId) {
        if (memories.isEmpty()) {
            return "- none";
        }
        List<String> sections = new ArrayList<>();
        appendSection(sections, "Profile memories", memories, LongTermMemoryType.PROFILE);
        appendSection(sections, "Semantic memories", memories, LongTermMemoryType.SEMANTIC);
        appendEpisodicSection(sections, memories, threadId);
        return sections.isEmpty() ? "- none" : String.join(System.lineSeparator() + System.lineSeparator(), sections);
    }

    private String formatMemory(LongTermMemoryRecord memory) {
        return "- %s: %s".formatted(
                blankFallback(memory.title(), "Untitled"),
                blankFallback(memory.content(), "")
        ).trim();
    }

    private void appendSection(List<String> sections,
                               String label,
                               List<LongTermMemoryRecord> memories,
                               LongTermMemoryType memoryType) {
        List<String> lines = memories.stream()
                .filter(memory -> memory.memoryType() == memoryType)
                .map(this::formatMemory)
                .toList();
        if (!lines.isEmpty()) {
            sections.add(label + ":" + System.lineSeparator() + String.join(System.lineSeparator(), lines));
        }
    }

    private void appendEpisodicSection(List<String> sections,
                                       List<LongTermMemoryRecord> memories,
                                       String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return;
        }
        List<String> lines = memories.stream()
                .filter(memory -> memory.memoryType() == LongTermMemoryType.EPISODIC)
                .filter(memory -> Objects.equals(threadId, memory.sourceThreadId()))
                .map(this::formatMemory)
                .toList();
        if (!lines.isEmpty()) {
            sections.add("Current thread episodic memories:" + System.lineSeparator()
                    + String.join(System.lineSeparator(), lines));
        }
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
