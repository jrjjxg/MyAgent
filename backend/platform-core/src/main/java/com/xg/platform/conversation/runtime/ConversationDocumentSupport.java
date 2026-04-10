package com.xg.platform.conversation.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.contracts.conversation.PostMessageRequest;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.workspace.ArtifactType;
import com.xg.platform.conversation.domain.ConversationRouteKind;
import com.xg.platform.document.application.ContextAssembler;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.document.domain.DocumentChunk;
import com.xg.platform.document.domain.RetrievedChunk;
import com.xg.platform.workspace.application.ArtifactService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ConversationDocumentSupport {

    private static final int DOC_MODEL_DOCUMENT_LIMIT = 6;
    private static final int DOC_MODEL_SECTION_LIMIT = 8;
    private static final int DOC_MODEL_MATCH_LIMIT = 5;
    private static final int DOC_MODEL_EVIDENCE_LIMIT = 3;
    private static final int DOC_MODEL_SNIPPET_LIMIT = 220;
    private static final int DOC_MODEL_SUMMARY_LIMIT = 320;

    private final ArtifactService artifactService;
    private final DocumentStore documentStore;
    private final ContextAssembler contextAssembler;
    private final DocumentIngestService documentIngestService;
    private final ObjectMapper objectMapper;
    private final DocumentQueryPlanner documentQueryPlanner = new DocumentQueryPlanner();

    ConversationDocumentSupport(ArtifactService artifactService,
                                DocumentStore documentStore,
                                ContextAssembler contextAssembler,
                                DocumentIngestService documentIngestService,
                                ObjectMapper objectMapper) {
        this.artifactService = artifactService;
        this.documentStore = documentStore;
        this.contextAssembler = contextAssembler;
        this.documentIngestService = documentIngestService;
        this.objectMapper = objectMapper;
    }

    List<DocumentRecord> maybeLoadDocuments(String userId,
                                            String threadId,
                                            ConversationRouteKind routeKind,
                                            String runId,
                                            List<String> selectedDocumentIds) {
        if (routeKind != ConversationRouteKind.DOCUMENT_QA) {
            return List.of();
        }
        List<DocumentRecord> documents = documentStore.listDocuments(userId, threadId);
        if (selectedDocumentIds != null && !selectedDocumentIds.isEmpty()) {
            Set<String> selected = new LinkedHashSet<>(selectedDocumentIds);
            documents = documents.stream()
                    .filter(document -> selected.contains(document.documentId()))
                    .toList();
        }
        return documentIngestService.ensureReadyDocuments(userId, threadId, documents, runId, delta -> {
        });
    }

    Map<String, Object> initializeDocumentQaState(String userId,
                                                  String question,
                                                  ConversationRouteKind routeKind,
                                                  List<DocumentRecord> documents) {
        if (routeKind != ConversationRouteKind.DOCUMENT_QA) {
            return Map.of();
        }
        List<String> sectionTitles = knownSectionTitles(userId, documents);
        DocumentQuestionType questionType = documentQueryPlanner.classify(question);
        String leadDocumentName = documents.isEmpty() ? "current document scope" : documents.get(0).name();
        DocumentQaScratchpad scratchpad = documentQueryPlanner.initializeScratchpad(question, questionType, leadDocumentName);
        String readingPlan = documentQueryPlanner.buildReadingPlan(questionType, leadDocumentName, sectionTitles);
        List<String> searchHints = documentQueryPlanner.buildSearchHints(question, scratchpad, sectionTitles);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(InteractionState.DOCUMENT_READING_PLAN, readingPlan);
        updates.put(InteractionState.DOCUMENT_WORKING_MEMORY, scratchpad.render());
        updates.put(InteractionState.DOCUMENT_PHASE, "PLAN");
        updates.put(InteractionState.DOCUMENT_SEARCH_HINTS, List.copyOf(searchHints));
        updates.put(InteractionState.DOCUMENT_SCRATCHPAD, scratchpad);
        return Map.copyOf(updates);
    }

    List<RetrievedChunk> retrieveChunks(String userId, String query, List<DocumentRecord> documents) {
        if (documents == null || documents.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        String normalizedQuery = query.trim();
        return contextAssembler.retrieve(normalizedQuery, documents, document -> loadChunks(userId, document), 8);
    }

    List<String> knownSectionTitles(String userId, List<DocumentRecord> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sectionTitles = new LinkedHashSet<>();
        for (DocumentRecord document : documents) {
            for (DocumentChunk chunk : loadChunks(userId, document)) {
                if (chunk.sectionTitle() == null || chunk.sectionTitle().isBlank()) {
                    continue;
                }
                sectionTitles.add(chunk.sectionTitle().trim());
                if (sectionTitles.size() >= 16) {
                    return documentQueryPlanner.knownSectionTitles(new ArrayList<>(sectionTitles));
                }
            }
        }
        return documentQueryPlanner.knownSectionTitles(new ArrayList<>(sectionTitles));
    }

    DocumentQaScratchpad defaultDocumentScratchpad(String question, List<DocumentRecord> documents) {
        DocumentQuestionType questionType = documentQueryPlanner.classify(question);
        String leadDocumentName = documents == null || documents.isEmpty()
                ? "current document scope"
                : documents.get(0).name();
        return documentQueryPlanner.initializeScratchpad(question, questionType, leadDocumentName);
    }

    DocumentQaScratchpad updateDocumentScratchpad(String question,
                                                  DocumentQaScratchpad scratchpad,
                                                  String toolName,
                                                  JsonNode toolOutput,
                                                  List<String> knownSectionTitles) {
        if (scratchpad == null) {
            return null;
        }
        if ("search_document".equals(toolName)) {
            return documentQueryPlanner.updateAfterSearch(question, scratchpad, toolOutput, knownSectionTitles);
        }
        if ("read_document".equals(toolName)) {
            return documentQueryPlanner.updateAfterRead(question, scratchpad, toolOutput);
        }
        return scratchpad;
    }

    String compressDocumentToolOutput(String toolName, JsonNode output) {
        if (output == null || output.isNull() || output.isMissingNode()) {
            return "";
        }
        ObjectNode compact = objectMapper.createObjectNode();
        compact.put("toolName", toolName == null ? "" : toolName);
        switch (toolName) {
            case "list_workspace_documents" -> compressListWorkspaceDocuments(output, compact);
            case "inspect_document" -> compressInspectDocument(output, compact);
            case "list_document_sections" -> compressListDocumentSections(output, compact);
            case "search_document" -> compressSearchDocument(output, compact);
            case "read_document" -> compressReadDocument(output, compact);
            default -> compact.put("summary", truncateForModel(output.toString(), DOC_MODEL_SUMMARY_LIMIT));
        }
        return compact.toString();
    }

    String determineDocumentPhase(String currentPhase,
                                  String lastToolName,
                                  DocumentQaScratchpad scratchpad) {
        return documentQueryPlanner.determinePhase(currentPhase, lastToolName, scratchpad);
    }

    List<String> buildDocumentSearchHints(String question,
                                          DocumentQaScratchpad scratchpad,
                                          List<String> knownSectionTitles) {
        return documentQueryPlanner.buildSearchHints(question, scratchpad, knownSectionTitles);
    }

    List<ThreadFileReference> uploadedFiles(String userId, List<ArtifactRecord> artifacts) {
        return artifacts.stream()
                .filter(artifact -> artifact.type() == ArtifactType.UPLOAD)
                .map(artifact -> new ThreadFileReference(
                        artifact.name(),
                        artifact.relativePath(),
                        artifactService.resolveArtifactPath(userId, artifact).toString(),
                        artifact.contentType(),
                        artifact.sizeBytes()
                ))
                .toList();
    }

    List<ThreadFileReference> resolveInputImages(String userId,
                                                 List<ArtifactRecord> artifacts,
                                                 PostMessageRequest request) {
        if (request == null || request.imageArtifactIds() == null || request.imageArtifactIds().isEmpty()) {
            return List.of();
        }
        Map<String, ArtifactRecord> artifactsById = new LinkedHashMap<>();
        for (ArtifactRecord artifact : artifacts) {
            artifactsById.put(artifact.artifactId(), artifact);
        }
        Set<String> uniqueIds = new LinkedHashSet<>(request.imageArtifactIds());
        List<ThreadFileReference> resolved = new ArrayList<>();
        for (String artifactId : uniqueIds) {
            ArtifactRecord artifact = artifactsById.get(artifactId);
            if (artifact == null || artifact.contentType() == null || !artifact.contentType().toLowerCase().startsWith("image/")) {
                continue;
            }
            resolved.add(new ThreadFileReference(
                    artifact.name(),
                    artifact.relativePath(),
                    artifactService.resolveArtifactPath(userId, artifact).toString(),
                    artifact.contentType(),
                    artifact.sizeBytes()
            ));
        }
        return List.copyOf(resolved);
    }

    private List<DocumentChunk> loadChunks(String userId, DocumentRecord document) {
        if (document.chunkIndexArtifactId() == null || document.chunkIndexArtifactId().isBlank()) {
            return List.of();
        }
        try {
            ArtifactRecord artifact = artifactService.findArtifactByWorkspace(userId, document.workspaceId(), document.chunkIndexArtifactId());
            Path artifactPath = artifactService.resolveArtifactPath(userId, artifact);
            return objectMapper.readValue(
                    artifactPath.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DocumentChunk.class)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load chunk index for document " + document.documentId(), exception);
        }
    }

    private void compressListWorkspaceDocuments(JsonNode output, ObjectNode compact) {
        compact.put("scope", output.path("scope").asText(""));
        compact.put("documentCount", output.path("documentCount").asInt(0));
        ArrayNode documents = compact.putArray("documents");
        int index = 0;
        for (JsonNode document : output.path("documents")) {
            if (index++ >= DOC_MODEL_DOCUMENT_LIMIT) {
                break;
            }
            documents.add(objectMapper.createObjectNode()
                    .put("documentId", document.path("documentId").asText(""))
                    .put("name", document.path("name").asText(""))
                    .put("status", document.path("status").asText(""))
                    .put("kind", document.path("kind").asText(""))
                    .put("pageCount", document.path("pageCount").asInt(0))
                    .put("chunkCount", document.path("chunkCount").asInt(0)));
        }
    }

    private void compressInspectDocument(JsonNode output, ObjectNode compact) {
        copyDocumentBasics(output, compact);
        compact.put("readable", output.path("readable").asBoolean(false));
        compact.put("sectionCount", output.path("sectionCount").asInt(0));
        if (output.hasNonNull("reason")) {
            compact.put("reason", output.path("reason").asText(""));
        }
        ArrayNode sections = compact.putArray("sectionPreview");
        int index = 0;
        for (JsonNode section : output.path("sectionPreview")) {
            if (index++ >= DOC_MODEL_SECTION_LIMIT) {
                break;
            }
            sections.add(compactSection(section));
        }
    }

    private void compressListDocumentSections(JsonNode output, ObjectNode compact) {
        compact.put("documentId", output.path("documentId").asText(""));
        compact.put("documentName", output.path("documentName").asText(""));
        compact.put("sectionCount", output.path("sectionCount").asInt(0));
        ArrayNode sections = compact.putArray("sections");
        int index = 0;
        for (JsonNode section : output.path("sections")) {
            if (index++ >= DOC_MODEL_SECTION_LIMIT) {
                break;
            }
            sections.add(compactSection(section));
        }
    }

    private void compressSearchDocument(JsonNode output, ObjectNode compact) {
        compact.put("query", output.path("query").asText(""));
        compact.put("matchCount", output.path("matchCount").asInt(0));
        if (output.hasNonNull("note")) {
            compact.put("note", output.path("note").asText(""));
        }
        ArrayNode matches = compact.putArray("matches");
        int index = 0;
        for (JsonNode match : output.path("matches")) {
            if (index++ >= DOC_MODEL_MATCH_LIMIT) {
                break;
            }
            matches.add(objectMapper.createObjectNode()
                    .put("documentName", match.path("documentName").asText(""))
                    .put("sectionTitle", match.path("sectionTitle").asText(""))
                    .put("pageStart", match.path("pageStart").asInt(0))
                    .put("pageEnd", match.path("pageEnd").asInt(0))
                    .put("score", match.path("score").asInt(0))
                    .put("snippet", truncateForModel(match.path("snippet").asText(""), DOC_MODEL_SNIPPET_LIMIT)));
        }
    }

    private void compressReadDocument(JsonNode output, ObjectNode compact) {
        compact.put("documentId", output.path("documentId").asText(""));
        compact.put("documentName", output.path("documentName").asText(""));
        compact.put("cursor", output.path("cursor").asText(""));
        compact.put("nextCursor", output.path("nextCursor").asText(""));
        compact.put("hasMore", output.path("hasMore").asBoolean(false));
        compact.put("chunkStart", output.path("chunkStart").asInt(0));
        compact.put("chunkEnd", output.path("chunkEnd").asInt(0));
        compact.put("pageStart", output.path("pageStart").asInt(0));
        compact.put("pageEnd", output.path("pageEnd").asInt(0));
        String content = output.path("content").asText("");
        compact.put("summary", truncateForModel(content, DOC_MODEL_SUMMARY_LIMIT));
        ArrayNode evidence = compact.putArray("evidence");
        for (String bullet : extractEvidenceBullets(content)) {
            evidence.add(bullet);
        }
    }

    private void copyDocumentBasics(JsonNode source, ObjectNode target) {
        target.put("documentId", source.path("documentId").asText(""));
        target.put("name", source.path("name").asText(""));
        target.put("status", source.path("status").asText(""));
        target.put("kind", source.path("kind").asText(""));
        target.put("pageCount", source.path("pageCount").asInt(0));
        target.put("chunkCount", source.path("chunkCount").asInt(0));
    }

    private ObjectNode compactSection(JsonNode section) {
        return objectMapper.createObjectNode()
                .put("sectionTitle", section.path("sectionTitle").asText(""))
                .put("pageStart", section.path("pageStart").asInt(0))
                .put("pageEnd", section.path("pageEnd").asInt(0))
                .put("chunkStart", section.path("chunkStart").asInt(0))
                .put("chunkEnd", section.path("chunkEnd").asInt(0));
    }

    private List<String> extractEvidenceBullets(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalized = content.replace("\r", "");
        LinkedHashSet<String> bullets = new LinkedHashSet<>();
        for (String rawLine : normalized.split("\n")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("##")) {
                continue;
            }
            bullets.add(truncateForModel(line, 140));
            if (bullets.size() >= DOC_MODEL_EVIDENCE_LIMIT) {
                break;
            }
        }
        if (bullets.isEmpty()) {
            bullets.add(truncateForModel(normalized.replaceAll("\\s+", " "), 140));
        }
        return List.copyOf(bullets);
    }

    private String truncateForModel(String text, int limit) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
