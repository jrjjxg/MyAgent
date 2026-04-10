package com.xg.platform.document.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import com.xg.platform.document.domain.DocumentChunk;

public class SemanticChunker {

    public static final String STRATEGY = "semantic-v1";
    public static final int TARGET_CHARS = 1000;
    public static final int MIN_CHARS = 700;
    public static final int MAX_CHARS = 1400;
    public static final int OVERLAP_CHARS = 180;

    private static final Pattern SENTENCE_BOUNDARY =
            Pattern.compile("(?<=[\\.!?;\\u3002\\uFF01\\uFF1F\\uFF1B])\\s+");

    public List<DocumentChunk> chunk(String documentId, String documentName, List<PageContent> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        List<TextSegment> segments = flattenPages(pages);
        if (segments.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> chunks = new ArrayList<>();
        List<TextSegment> currentSegments = new ArrayList<>();
        for (TextSegment segment : segments) {
            int currentLength = render(currentSegments).length();
            if (!currentSegments.isEmpty()
                    && currentLength >= MIN_CHARS
                    && (currentLength >= TARGET_CHARS || combinedLength(currentSegments, segment) > MAX_CHARS)) {
                chunks.add(toChunk(documentId, documentName, chunks.size() + 1, currentSegments));
                currentSegments = overlapSegments(currentSegments);
            }
            if (currentSegments.isEmpty()) {
                currentSegments.add(segment);
                continue;
            }
            if (combinedLength(currentSegments, segment) <= MAX_CHARS) {
                currentSegments.add(segment);
                continue;
            }
            chunks.add(toChunk(documentId, documentName, chunks.size() + 1, currentSegments));
            currentSegments = overlapSegments(currentSegments);
            if (combinedLength(currentSegments, segment) > MAX_CHARS && !currentSegments.isEmpty()) {
                chunks.add(toChunk(documentId, documentName, chunks.size() + 1, currentSegments));
                currentSegments = new ArrayList<>();
            }
            currentSegments.add(segment);
        }
        if (!currentSegments.isEmpty()) {
            chunks.add(toChunk(documentId, documentName, chunks.size() + 1, currentSegments));
        }
        return List.copyOf(chunks);
    }

    private List<TextSegment> flattenPages(List<PageContent> pages) {
        List<TextSegment> segments = new ArrayList<>();
        String currentSectionTitle = null;
        for (PageContent page : pages) {
            if (page == null || page.text() == null || page.text().isBlank()) {
                continue;
            }
            for (ParagraphBlock block : splitParagraphs(page.text())) {
                if (block.text().isBlank()) {
                    continue;
                }
                if (isHeading(block)) {
                    currentSectionTitle = normalizeHeading(block.text());
                    continue;
                }
                for (String piece : splitToSize(block.text())) {
                    if (!piece.isBlank()) {
                        segments.add(new TextSegment(piece, page.pageNumber(), page.pageNumber(), currentSectionTitle));
                    }
                }
            }
        }
        return List.copyOf(segments);
    }

    private List<ParagraphBlock> splitParagraphs(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        List<ParagraphBlock> blocks = new ArrayList<>();
        for (String block : normalized.split("\\n\\s*\\n+")) {
            String trimmedBlock = block == null ? "" : block.trim();
            if (trimmedBlock.isBlank()) {
                continue;
            }
            String[] lines = trimmedBlock.split("\\n");
            List<String> nonBlankLines = new ArrayList<>();
            for (String line : lines) {
                String trimmedLine = line == null ? "" : line.trim();
                if (!trimmedLine.isBlank()) {
                    nonBlankLines.add(trimmedLine);
                }
            }
            if (nonBlankLines.isEmpty()) {
                continue;
            }
            blocks.add(new ParagraphBlock(
                    String.join(" ", nonBlankLines).replaceAll("\\s+", " ").trim(),
                    nonBlankLines.size() == 1
            ));
        }
        return List.copyOf(blocks);
    }

    private boolean isHeading(ParagraphBlock block) {
        String text = block.text();
        if (text.startsWith("#")) {
            return true;
        }
        if (!block.singleLine() || text.length() > 80) {
            return false;
        }
        if (text.endsWith(":") || text.endsWith("\uFF1A")) {
            return true;
        }
        if (endsWithTerminalPunctuation(text)) {
            return false;
        }
        return wordCount(text) <= 10;
    }

    private boolean endsWithTerminalPunctuation(String text) {
        return text.endsWith(".")
                || text.endsWith("!")
                || text.endsWith("?")
                || text.endsWith("\u3002")
                || text.endsWith("\uFF01")
                || text.endsWith("\uFF1F");
    }

    private int wordCount(String text) {
        return text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
    }

    private String normalizeHeading(String text) {
        String normalized = text == null ? "" : text.trim();
        normalized = normalized.replaceFirst("^#+\\s*", "");
        if (normalized.endsWith(":") || normalized.endsWith("\uFF1A")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private List<String> splitToSize(String paragraph) {
        if (paragraph.length() <= MAX_CHARS) {
            return List.of(paragraph);
        }
        List<String> pieces = new ArrayList<>();
        List<String> sentences = splitSentences(paragraph);
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.length() > MAX_CHARS) {
                flushCurrent(pieces, current);
                pieces.addAll(splitByCharacters(trimmed));
                continue;
            }
            if (current.length() == 0) {
                current.append(trimmed);
                continue;
            }
            if (current.length() + 1 + trimmed.length() <= MAX_CHARS) {
                current.append(' ').append(trimmed);
                continue;
            }
            flushCurrent(pieces, current);
            current.append(trimmed);
        }
        flushCurrent(pieces, current);
        return List.copyOf(pieces);
    }

    private List<String> splitSentences(String paragraph) {
        String[] parts = SENTENCE_BOUNDARY.split(paragraph);
        if (parts.length <= 1) {
            return splitByCharacters(paragraph);
        }
        return List.of(parts);
    }

    private List<String> splitByCharacters(String value) {
        List<String> pieces = new ArrayList<>();
        for (int start = 0; start < value.length(); start += MAX_CHARS) {
            int end = Math.min(value.length(), start + MAX_CHARS);
            pieces.add(value.substring(start, end).trim());
        }
        return List.copyOf(pieces);
    }

    private void flushCurrent(List<String> pieces, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        pieces.add(current.toString().trim());
        current.setLength(0);
    }

    private int combinedLength(List<TextSegment> currentSegments, TextSegment candidate) {
        return render(currentSegments).length()
                + (currentSegments.isEmpty() ? 0 : 2)
                + candidate.text().length();
    }

    private List<TextSegment> overlapSegments(List<TextSegment> segments) {
        List<TextSegment> overlap = new ArrayList<>();
        int length = 0;
        for (int index = segments.size() - 1; index >= 0; index--) {
            TextSegment segment = segments.get(index);
            overlap.add(0, segment);
            length += segment.text().length();
            if (length >= OVERLAP_CHARS) {
                break;
            }
        }
        return overlap;
    }

    private DocumentChunk toChunk(String documentId, String documentName, int chunkOrder, List<TextSegment> segments) {
        String text = render(segments);
        int pageStart = segments.stream().mapToInt(TextSegment::pageStart).min().orElse(1);
        int pageEnd = segments.stream().mapToInt(TextSegment::pageEnd).max().orElse(pageStart);
        String sectionTitle = segments.stream()
                .map(TextSegment::sectionTitle)
                .filter(title -> title != null && !title.isBlank())
                .findFirst()
                .orElse(null);
        return new DocumentChunk(
                documentId + "-chunk-" + chunkOrder,
                documentId,
                documentName,
                pageStart,
                pageEnd,
                text,
                sectionTitle,
                chunkOrder
        );
    }

    private String render(List<TextSegment> segments) {
        StringBuilder builder = new StringBuilder();
        for (TextSegment segment : segments) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(segment.text());
        }
        return builder.toString();
    }

    public record PageContent(int pageNumber, String text) {
    }

    private record ParagraphBlock(String text, boolean singleLine) {
    }

    private record TextSegment(String text, int pageStart, int pageEnd, String sectionTitle) {
    }
}
