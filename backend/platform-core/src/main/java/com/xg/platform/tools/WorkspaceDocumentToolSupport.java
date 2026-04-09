package com.xg.platform.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.document.DocumentStatus;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.memory.ContextAssembler;
import com.xg.platform.memory.DocumentChunk;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.memory.RetrievedChunk;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class WorkspaceDocumentToolSupport {

    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int MAX_SEARCH_LIMIT = 12;
    private static final int DEFAULT_READ_CHUNKS = 4;
    private static final int MAX_READ_CHUNKS = 8;
    private static final int SECTION_PREVIEW_LIMIT = 8;
    private static final int SNIPPET_LIMIT = 320;

    private final DocumentStore documentStore;
    private final ArtifactService artifactService;
    private final WorkspaceManager workspaceManager;
    private final ContextAssembler contextAssembler;
    private final ObjectMapper objectMapper;

    public WorkspaceDocumentToolSupport(DocumentStore documentStore,
                                        ArtifactService artifactService,
                                        WorkspaceManager workspaceManager,
                                        ContextAssembler contextAssembler,
                                        ObjectMapper objectMapper) {
        this.documentStore = documentStore;
        this.artifactService = artifactService;
        this.workspaceManager = workspaceManager;
        this.contextAssembler = contextAssembler;
        this.objectMapper = objectMapper;
    }

    public ToolExecutionResult execute(ToolExecutionRequest request) {
        return switch (request.tool().name()) {
            case "list_workspace_documents" -> listWorkspaceDocuments(request);
            case "inspect_document" -> inspectDocument(request);
            case "list_document_sections" -> listDocumentSections(request);
            case "search_document" -> searchDocument(request);
            case "read_document" -> readDocument(request);
            default -> throw new IllegalArgumentException("Unsupported workspace document tool: " + request.tool().name());
        };
    }

    private ToolExecutionResult listWorkspaceDocuments(ToolExecutionRequest request) {
        List<DocumentRecord> documents = scopedDocuments(request);
        ArrayNode items = objectMapper.createArrayNode();
        for (DocumentRecord document : documents) {
            items.add(documentSummary(request.userId(), document));
        }
        ObjectNode output = objectMapper.createObjectNode();
        output.put("scope", request.allowedDocumentIds().isEmpty() ? "workspace" : "selected");
        output.put("documentCount", items.size());
        output.set("documents", items);
        return result("list_workspace_documents", output);
    }

    private ToolExecutionResult inspectDocument(ToolExecutionRequest request) {
        DocumentRecord document = requireDocument(request, stringArg(request.arguments(), "documentId", true));
        ObjectNode output = documentSummary(request.userId(), document);
        boolean readable = isReadable(document);
        output.put("readable", readable);
        output.put("selectedScope", !request.allowedDocumentIds().isEmpty());
        if (!readable) {
            output.put("reason", "Document status is %s. Wait for READY before reading.".formatted(document.status()));
            output.set("sectionPreview", objectMapper.createArrayNode());
            return result("inspect_document", output);
        }

        List<SectionSummary> sections = summarizeSections(loadChunks(request.userId(), document));
        output.set("sectionPreview", sectionSummaryArray(sections, SECTION_PREVIEW_LIMIT));
        output.put("sectionCount", sections.size());
        return result("inspect_document", output);
    }

    private ToolExecutionResult listDocumentSections(ToolExecutionRequest request) {
        DocumentRecord document = requireReadableDocument(request, stringArg(request.arguments(), "documentId", true));
        List<SectionSummary> sections = summarizeSections(loadChunks(request.userId(), document));
        ObjectNode output = objectMapper.createObjectNode();
        output.put("documentId", document.documentId());
        output.put("documentName", document.name());
        output.put("sectionCount", sections.size());
        output.set("sections", sectionSummaryArray(sections, sections.size()));
        return result("list_document_sections", output);
    }

    private ToolExecutionResult searchDocument(ToolExecutionRequest request) {
        String query = stringArg(request.arguments(), "query", true);
        String requestedDocumentId = stringArg(request.arguments(), "documentId", false);
        int limit = intArg(request.arguments(), "limit", DEFAULT_SEARCH_LIMIT, 1, MAX_SEARCH_LIMIT);
        List<DocumentRecord> documents = requestedDocumentId == null
                ? readableDocuments(scopedDocuments(request))
                : List.of(requireReadableDocument(request, requestedDocumentId));
        List<RetrievedChunk> matches = contextAssembler.retrieve(
                query,
                documents,
                document -> loadChunks(request.userId(), document),
                limit
        );
        ObjectNode output = objectMapper.createObjectNode();
        output.put("query", query);
        output.put("matchCount", matches.size());
        ArrayNode results = output.putArray("matches");
        for (RetrievedChunk match : matches) {
            results.add(searchMatch(match));
        }
        if (matches.isEmpty()) {
            output.put("note", "No matching excerpts were found in the current document scope.");
        }
        return result("search_document", output);
    }

    private ToolExecutionResult readDocument(ToolExecutionRequest request) {
        DocumentRecord document = requireReadableDocument(request, stringArg(request.arguments(), "documentId", true));
        List<DocumentChunk> chunks = orderedChunks(loadChunks(request.userId(), document));
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document has no readable chunks: " + document.name());
        }

        String cursor = stringArg(request.arguments(), "cursor", false);
        Integer pageStart = nullableIntArg(request.arguments(), "pageStart");
        Integer pageEnd = nullableIntArg(request.arguments(), "pageEnd");
        int maxChunks = intArg(request.arguments(), "maxChunks", DEFAULT_READ_CHUNKS, 1, MAX_READ_CHUNKS);

        List<DocumentChunk> selection;
        int startIndex;
        if (cursor != null && !cursor.isBlank()) {
            startIndex = resolveCursorIndex(cursor, chunks);
            selection = chunks.subList(startIndex, Math.min(chunks.size(), startIndex + maxChunks));
        } else if (pageStart != null || pageEnd != null) {
            int effectiveStart = pageStart == null ? 1 : Math.max(1, pageStart);
            int effectiveEnd = pageEnd == null ? effectiveStart : Math.max(effectiveStart, pageEnd);
            selection = chunks.stream()
                    .filter(chunk -> overlaps(chunk, effectiveStart, effectiveEnd))
                    .limit(maxChunks)
                    .toList();
            if (selection.isEmpty()) {
                throw new IllegalArgumentException("No readable chunks overlap pages %d-%d in %s"
                        .formatted(effectiveStart, effectiveEnd, document.name()));
            }
            startIndex = chunks.indexOf(selection.get(0));
        } else {
            startIndex = 0;
            selection = chunks.subList(0, Math.min(chunks.size(), maxChunks));
        }

        int nextIndex = startIndex + selection.size();
        ObjectNode output = objectMapper.createObjectNode();
        output.put("documentId", document.documentId());
        output.put("documentName", document.name());
        output.put("cursor", "chunk:%d".formatted(chunkOrder(selection.get(0), startIndex)));
        output.put("nextCursor", nextIndex < chunks.size()
                ? "chunk:%d".formatted(chunkOrder(chunks.get(nextIndex), nextIndex))
                : "");
        output.put("hasMore", nextIndex < chunks.size());
        output.put("chunkStart", chunkOrder(selection.get(0), startIndex));
        output.put("chunkEnd", chunkOrder(selection.get(selection.size() - 1), startIndex + selection.size() - 1));
        output.put("pageStart", selection.stream().mapToInt(DocumentChunk::pageStart).min().orElse(1));
        output.put("pageEnd", selection.stream().mapToInt(DocumentChunk::pageEnd).max().orElse(1));
        output.put("content", renderReadableContent(selection));
        return result("read_document", output);
    }

    private List<DocumentRecord> scopedDocuments(ToolExecutionRequest request) {
        if (request.threadId() == null || request.threadId().isBlank()) {
            throw new IllegalArgumentException("Workspace document tools require a thread context");
        }
        List<DocumentRecord> documents = documentStore.listDocuments(request.userId(), request.threadId());
        if (request.allowedDocumentIds().isEmpty()) {
            return documents;
        }
        Map<String, DocumentRecord> documentsById = new LinkedHashMap<>();
        for (DocumentRecord document : documents) {
            documentsById.put(document.documentId(), document);
        }
        List<DocumentRecord> scoped = new ArrayList<>();
        for (String documentId : request.allowedDocumentIds()) {
            DocumentRecord document = documentsById.get(documentId);
            if (document != null) {
                scoped.add(document);
            }
        }
        return List.copyOf(scoped);
    }

    private DocumentRecord requireDocument(ToolExecutionRequest request, String documentId) {
        return scopedDocuments(request).stream()
                .filter(document -> document.documentId().equals(documentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Document is not available in the current scope: " + documentId));
    }

    private DocumentRecord requireReadableDocument(ToolExecutionRequest request, String documentId) {
        DocumentRecord document = requireDocument(request, documentId);
        if (!isReadable(document)) {
            throw new IllegalArgumentException("Document is not ready for reading: %s (%s)"
                    .formatted(document.name(), document.status()));
        }
        return document;
    }

    private List<DocumentRecord> readableDocuments(List<DocumentRecord> documents) {
        List<DocumentRecord> readable = documents.stream()
                .filter(this::isReadable)
                .toList();
        if (readable.isEmpty()) {
            throw new IllegalArgumentException("No readable documents are available in the current scope.");
        }
        return readable;
    }

    private boolean isReadable(DocumentRecord document) {
        return document.status() == DocumentStatus.READY
                && document.chunkIndexArtifactId() != null
                && !document.chunkIndexArtifactId().isBlank();
    }

    private ObjectNode documentSummary(String userId, DocumentRecord document) {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("documentId", document.documentId());
        output.put("name", document.name());
        output.put("status", document.status().name());
        output.put("workspaceId", document.workspaceId());
        output.put("updatedAt", document.updatedAt().toString());

        DocumentManifest manifest = loadManifest(userId, document);
        if (manifest != null) {
            putNullableText(output, "kind", manifest.kind());
            putNullableInt(output, "pageCount", manifest.pageCount());
            putNullableInt(output, "chunkCount", manifest.chunkCount());
            putNullableText(output, "chunkStrategy", manifest.chunkStrategy());
        } else {
            putNullableText(output, "kind", null);
            putNullableInt(output, "pageCount", null);
            putNullableInt(output, "chunkCount", null);
            putNullableText(output, "chunkStrategy", null);
        }
        output.put("hasRenderedPages", hasRenderedPages(userId, document));
        return output;
    }

    private DocumentManifest loadManifest(String userId, DocumentRecord document) {
        Path path = workspaceManager.resolveWorkspacePath(
                userId,
                document.workspaceId(),
                WorkspaceArea.WORKSPACE,
                "documents/%s/manifest.json".formatted(document.documentId())
        );
        if (!Files.exists(path)) {
            return null;
        }
        try {
            JsonNode manifest = objectMapper.readTree(path.toFile());
            return new DocumentManifest(
                    textOrNull(manifest, "kind"),
                    intOrNull(manifest, "pageCount"),
                    intOrNull(manifest, "chunkCount"),
                    textOrNull(manifest, "chunkStrategy")
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read document manifest for " + document.documentId(), exception);
        }
    }

    private List<DocumentChunk> loadChunks(String userId, DocumentRecord document) {
        if (document.chunkIndexArtifactId() == null || document.chunkIndexArtifactId().isBlank()) {
            return List.of();
        }
        try {
            ArtifactRecord artifact = artifactService.findArtifactByWorkspace(userId, document.workspaceId(), document.chunkIndexArtifactId());
            Path path = artifactService.resolveArtifactPath(userId, artifact);
            return objectMapper.readValue(
                    path.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DocumentChunk.class)
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read chunk index for " + document.documentId(), exception);
        } catch (NoSuchElementException exception) {
            throw new IllegalArgumentException("Chunk index is missing for document " + document.name(), exception);
        }
    }

    private boolean hasRenderedPages(String userId, DocumentRecord document) {
        Path pagesDir = workspaceManager.resolveWorkspacePath(
                userId,
                document.workspaceId(),
                WorkspaceArea.WORKSPACE,
                "documents/%s/pages".formatted(document.documentId())
        );
        if (!Files.isDirectory(pagesDir)) {
            return false;
        }
        try (var stream = Files.list(pagesDir)) {
            return stream.findAny().isPresent();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to inspect rendered pages for " + document.documentId(), exception);
        }
    }

    private List<SectionSummary> summarizeSections(List<DocumentChunk> chunks) {
        List<DocumentChunk> ordered = orderedChunks(chunks);
        if (ordered.isEmpty()) {
            return List.of();
        }
        List<SectionSummary> sections = new ArrayList<>();
        SectionSummary current = null;
        for (int index = 0; index < ordered.size(); index++) {
            DocumentChunk chunk = ordered.get(index);
            String label = sectionLabel(chunk);
            int chunkOrder = chunkOrder(chunk, index);
            if (current == null || !current.sectionTitle().equals(label)) {
                current = new SectionSummary(label, chunk.pageStart(), chunk.pageEnd(), chunkOrder, chunkOrder);
                sections.add(current);
                continue;
            }
            current = current.extend(chunk.pageStart(), chunk.pageEnd(), chunkOrder);
            sections.set(sections.size() - 1, current);
        }
        return List.copyOf(sections);
    }

    private List<DocumentChunk> orderedChunks(List<DocumentChunk> chunks) {
        return chunks.stream()
                .sorted(Comparator
                        .comparingInt((DocumentChunk chunk) -> chunk.chunkOrder() == null ? Integer.MAX_VALUE : chunk.chunkOrder())
                        .thenComparingInt(DocumentChunk::pageStart)
                        .thenComparing(DocumentChunk::chunkId))
                .toList();
    }

    private ArrayNode sectionSummaryArray(List<SectionSummary> sections, int limit) {
        ArrayNode items = objectMapper.createArrayNode();
        sections.stream()
                .limit(Math.max(0, limit))
                .forEach(section -> items.add(objectMapper.createObjectNode()
                        .put("sectionTitle", section.sectionTitle())
                        .put("pageStart", section.pageStart())
                        .put("pageEnd", section.pageEnd())
                        .put("chunkStart", section.chunkStart())
                        .put("chunkEnd", section.chunkEnd())));
        return items;
    }

    private ObjectNode searchMatch(RetrievedChunk retrievedChunk) {
        DocumentChunk chunk = retrievedChunk.chunk();
        return objectMapper.createObjectNode()
                .put("documentId", chunk.documentId())
                .put("documentName", chunk.documentName())
                .put("pageStart", chunk.pageStart())
                .put("pageEnd", chunk.pageEnd())
                .put("chunkId", chunk.chunkId())
                .put("sectionTitle", chunk.sectionTitle() == null ? "" : chunk.sectionTitle())
                .put("snippet", truncate(chunk.text(), SNIPPET_LIMIT))
                .put("score", retrievedChunk.score());
    }

    private String renderReadableContent(List<DocumentChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        Set<String> renderedHeadings = new LinkedHashSet<>();
        for (DocumentChunk chunk : chunks) {
            String heading = chunk.sectionTitle();
            if (heading != null && !heading.isBlank() && renderedHeadings.add(heading.trim())) {
                if (!builder.isEmpty()) {
                    builder.append(System.lineSeparator()).append(System.lineSeparator());
                }
                builder.append("## ").append(heading.trim()).append(System.lineSeparator());
            } else if (!builder.isEmpty()) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append(chunk.text() == null ? "" : chunk.text().trim());
        }
        return builder.toString().trim();
    }

    private int resolveCursorIndex(String cursor, List<DocumentChunk> chunks) {
        String normalized = cursor.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("chunk:")) {
            throw new IllegalArgumentException("Unsupported cursor format. Expected chunk:<order>.");
        }
        int order;
        try {
            order = Integer.parseInt(normalized.substring("chunk:".length()).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Unsupported cursor format. Expected chunk:<order>.", exception);
        }
        for (int index = 0; index < chunks.size(); index++) {
            if (chunkOrder(chunks.get(index), index) >= order) {
                return index;
            }
        }
        throw new IllegalArgumentException("Cursor is past the end of the document: " + cursor);
    }

    private boolean overlaps(DocumentChunk chunk, int pageStart, int pageEnd) {
        return chunk.pageStart() <= pageEnd && chunk.pageEnd() >= pageStart;
    }

    private int chunkOrder(DocumentChunk chunk, int fallbackIndex) {
        return chunk.chunkOrder() == null ? fallbackIndex + 1 : chunk.chunkOrder();
    }

    private String sectionLabel(DocumentChunk chunk) {
        if (chunk.sectionTitle() != null && !chunk.sectionTitle().isBlank()) {
            return chunk.sectionTitle().trim();
        }
        if (chunk.pageStart() == chunk.pageEnd()) {
            return "p.%d".formatted(chunk.pageStart());
        }
        return "p.%d-%d".formatted(chunk.pageStart(), chunk.pageEnd());
    }

    private ToolExecutionResult result(String toolName, JsonNode output) {
        return new ToolExecutionResult(toolName, output, false, "ok");
    }

    private String stringArg(JsonNode arguments, String field, boolean required) {
        String value = arguments == null ? "" : arguments.path(field).asText("").trim();
        if (required && value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.isBlank() ? null : value;
    }

    private Integer nullableIntArg(JsonNode arguments, String field) {
        if (arguments == null || arguments.path(field).isMissingNode() || arguments.path(field).isNull()) {
            return null;
        }
        if (!arguments.path(field).canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return arguments.path(field).asInt();
    }

    private int intArg(JsonNode arguments, String field, int defaultValue, int minValue, int maxValue) {
        Integer value = nullableIntArg(arguments, field);
        int resolved = value == null ? defaultValue : value;
        return Math.max(minValue, Math.min(maxValue, resolved));
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isBlank() ? null : value;
    }

    private Integer intOrNull(JsonNode node, String field) {
        return node.path(field).canConvertToInt() ? node.path(field).asInt() : null;
    }

    private void putNullableText(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) {
            node.putNull(field);
            return;
        }
        node.put(field, value);
    }

    private void putNullableInt(ObjectNode node, String field, Integer value) {
        if (value == null) {
            node.putNull(field);
            return;
        }
        node.put(field, value);
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private record DocumentManifest(String kind,
                                    Integer pageCount,
                                    Integer chunkCount,
                                    String chunkStrategy) {
    }

    private record SectionSummary(String sectionTitle,
                                  int pageStart,
                                  int pageEnd,
                                  int chunkStart,
                                  int chunkEnd) {

        private SectionSummary extend(int nextPageStart, int nextPageEnd, int nextChunkOrder) {
            return new SectionSummary(
                    sectionTitle,
                    Math.min(pageStart, nextPageStart),
                    Math.max(pageEnd, nextPageEnd),
                    chunkStart,
                    nextChunkOrder
            );
        }
    }
}
