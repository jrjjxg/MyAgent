package com.xg.platform.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport;
import com.xg.platform.contracts.workspace.ArtifactType;
import com.xg.platform.contracts.workspace.ArtifactVisibility;
import com.xg.platform.contracts.workspace.RegisterArtifactCommand;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.document.application.ContextAssembler;
import com.xg.platform.document.domain.DocumentChunk;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.xg.platform.tooling.application.WorkspaceDocumentToolSupport;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolExecutionRequest;
import com.xg.platform.tooling.domain.ToolExecutionResult;
import com.xg.platform.tooling.domain.ToolGroup;

class WorkspaceDocumentToolSupportTest {

    private static final String USER_ID = "user-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String RUN_ID = "run-1";

    @TempDir
    Path tempDir;

    @Test
    void listWorkspaceDocumentsRespectsSelectedScopeAndManifest() throws Exception {
        Harness harness = createHarness();
        DocumentRecord selected = harness.createReadyDocument(
                "paper-a.pdf",
                List.of(
                        new DocumentChunk("a-1", "doc-a", "paper-a.pdf", 1, 1, "Alpha abstract", "Abstract", 1)
                )
        );
        harness.createReadyDocument(
                "paper-b.pdf",
                List.of(
                        new DocumentChunk("b-1", "doc-b", "paper-b.pdf", 1, 1, "Beta abstract", "Abstract", 1)
                )
        );

        ToolExecutionResult result = harness.support().execute(new ToolExecutionRequest(
                USER_ID,
                harness.threadId(),
                RUN_ID,
                builtinTool(harness.objectMapper(), "list_workspace_documents"),
                harness.objectMapper().createObjectNode(),
                null,
                List.of(),
                List.of(selected.documentId())
        ));

        assertThat(result.output().path("scope").asText()).isEqualTo("selected");
        assertThat(result.output().path("documentCount").asInt()).isEqualTo(1);
        assertThat(result.output().path("documents").get(0).path("documentId").asText()).isEqualTo(selected.documentId());
        assertThat(result.output().path("documents").get(0).path("kind").asText()).isEqualTo("pdf");
        assertThat(result.output().path("documents").get(0).path("chunkCount").asInt()).isEqualTo(1);
    }

    @Test
    void inspectDocumentReturnsSectionPreviewForReadableDocument() throws Exception {
        Harness harness = createHarness();
        DocumentRecord document = harness.createReadyDocument(
                "stack-lstm.pdf",
                List.of(
                        new DocumentChunk("chunk-1", "doc-1", "stack-lstm.pdf", 1, 1, "Abstract content", "Abstract", 1),
                        new DocumentChunk("chunk-2", "doc-1", "stack-lstm.pdf", 2, 3, "Method content", "Method", 2)
                )
        );

        ToolExecutionResult result = harness.support().execute(new ToolExecutionRequest(
                USER_ID,
                harness.threadId(),
                RUN_ID,
                builtinTool(harness.objectMapper(), "inspect_document"),
                harness.objectMapper().createObjectNode().put("documentId", document.documentId()),
                null,
                List.of(),
                List.of(document.documentId())
        ));

        assertThat(result.output().path("readable").asBoolean()).isTrue();
        assertThat(result.output().path("sectionPreview")).hasSize(2);
        assertThat(result.output().path("sectionPreview").get(0).path("sectionTitle").asText()).isEqualTo("Abstract");
    }

    @Test
    void searchDocumentReturnsMatchesWithPages() throws Exception {
        Harness harness = createHarness();
        DocumentRecord document = harness.createReadyDocument(
                "transformers.pdf",
                List.of(
                        new DocumentChunk("chunk-1", "doc-1", "transformers.pdf", 1, 1, "Introduction to attention and transformers", "Introduction", 1),
                        new DocumentChunk("chunk-2", "doc-1", "transformers.pdf", 2, 2, "Experimental setup", "Method", 2)
                )
        );

        ToolExecutionResult result = harness.support().execute(new ToolExecutionRequest(
                USER_ID,
                harness.threadId(),
                RUN_ID,
                builtinTool(harness.objectMapper(), "search_document"),
                harness.objectMapper().createObjectNode()
                        .put("query", "attention transformers")
                        .put("documentId", document.documentId())
                        .put("limit", 3),
                null,
                List.of(),
                List.of(document.documentId())
        ));

        JsonNode firstMatch = result.output().path("matches").get(0);
        assertThat(result.output().path("matchCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(firstMatch.path("documentId").asText()).isEqualTo(document.documentId());
        assertThat(firstMatch.path("pageStart").asInt()).isEqualTo(1);
        assertThat(firstMatch.path("snippet").asText()).contains("transformers");
    }

    @Test
    void searchDocumentBoostsSectionTitleAndSearchableTextMatches() throws Exception {
        Harness harness = createHarness();
        DocumentRecord document = harness.createReadyDocument(
                "ranking.pdf",
                List.of(
                        new DocumentChunk("chunk-1", "doc-1", "ranking.pdf", 1, 1, "This section explains the architecture in detail.", "Method Overview", 1),
                        new DocumentChunk("chunk-2", "doc-1", "ranking.pdf", 2, 2, "A short appendix note mentions the method once.", "Appendix", 2)
                )
        );

        ToolExecutionResult result = harness.support().execute(new ToolExecutionRequest(
                USER_ID,
                harness.threadId(),
                RUN_ID,
                builtinTool(harness.objectMapper(), "search_document"),
                harness.objectMapper().createObjectNode()
                        .put("query", "method overview architecture")
                        .put("documentId", document.documentId())
                        .put("limit", 2),
                null,
                List.of(),
                List.of(document.documentId())
        ));

        JsonNode firstMatch = result.output().path("matches").get(0);
        assertThat(firstMatch.path("sectionTitle").asText()).isEqualTo("Method Overview");
        assertThat(firstMatch.path("snippet").asText()).contains("architecture");
    }

    @Test
    void readDocumentSupportsCursorContinuation() throws Exception {
        Harness harness = createHarness();
        DocumentRecord document = harness.createReadyDocument(
                "cursor.pdf",
                List.of(
                        new DocumentChunk("chunk-1", "doc-1", "cursor.pdf", 1, 1, "First page", "Intro", 1),
                        new DocumentChunk("chunk-2", "doc-1", "cursor.pdf", 2, 2, "Second page", "Body", 2),
                        new DocumentChunk("chunk-3", "doc-1", "cursor.pdf", 3, 3, "Third page", "Body", 3)
                )
        );

        ToolExecutionResult first = harness.support().execute(new ToolExecutionRequest(
                USER_ID,
                harness.threadId(),
                RUN_ID,
                builtinTool(harness.objectMapper(), "read_document"),
                harness.objectMapper().createObjectNode()
                        .put("documentId", document.documentId())
                        .put("maxChunks", 2),
                null,
                List.of(),
                List.of(document.documentId())
        ));

        ToolExecutionResult second = harness.support().execute(new ToolExecutionRequest(
                USER_ID,
                harness.threadId(),
                RUN_ID,
                builtinTool(harness.objectMapper(), "read_document"),
                harness.objectMapper().createObjectNode()
                        .put("documentId", document.documentId())
                        .put("cursor", first.output().path("nextCursor").asText()),
                null,
                List.of(),
                List.of(document.documentId())
        ));

        assertThat(first.output().path("content").asText()).contains("First page", "Second page");
        assertThat(first.output().path("nextCursor").asText()).isEqualTo("chunk:3");
        assertThat(first.output().path("hasMore").asBoolean()).isTrue();
        assertThat(second.output().path("content").asText()).contains("Third page");
        assertThat(second.output().path("hasMore").asBoolean()).isFalse();
    }

    @Test
    void readDocumentRejectsUnreadableDocument() {
        Harness harness = createHarness();
        DocumentRecord uploaded = harness.documentStore().createUploaded(
                USER_ID,
                WORKSPACE_ID,
                harness.threadId(),
                "artifact-source",
                "pending.pdf"
        );

        assertThatThrownBy(() -> harness.support().execute(new ToolExecutionRequest(
                USER_ID,
                harness.threadId(),
                RUN_ID,
                builtinTool(harness.objectMapper(), "read_document"),
                harness.objectMapper().createObjectNode().put("documentId", uploaded.documentId()),
                null,
                List.of(),
                List.of(uploaded.documentId())
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not ready for reading");
    }

    private Harness createHarness() {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadService threadRuntimeService = new ThreadService(new InMemoryRuntimeSupport.InMemoryThreadRepository());
        String threadId = threadRuntimeService.createThread(USER_ID, WORKSPACE_ID, "Docs Thread").threadId();
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        DocumentStore documentStore = new DocumentStore(workspaceManager, threadRuntimeService, objectMapper);
        WorkspaceDocumentToolSupport support = new WorkspaceDocumentToolSupport(
                documentStore,
                artifactService,
                workspaceManager,
                new ContextAssembler(),
                objectMapper
        );
        return new Harness(threadId, workspaceManager, artifactService, documentStore, support, objectMapper);
    }

    private static ToolDescriptor builtinTool(ObjectMapper objectMapper, String name) {
        return new ToolDescriptor(name, "test tool", objectMapper.createObjectNode(), ToolGroup.DOCUMENTS, "builtin");
    }

    private record Harness(String threadId,
                           WorkspaceManager workspaceManager,
                           ArtifactService artifactService,
                           DocumentStore documentStore,
                           WorkspaceDocumentToolSupport support,
                           ObjectMapper objectMapper) {

        private DocumentRecord createReadyDocument(String name, List<DocumentChunk> chunks) throws Exception {
            DocumentRecord uploaded = documentStore.createUploaded(USER_ID, WORKSPACE_ID, threadId, "source-" + name, name);
            List<DocumentChunk> normalizedChunks = chunks.stream()
                    .map(chunk -> new DocumentChunk(
                            chunk.chunkId(),
                            uploaded.documentId(),
                            name,
                            chunk.pageStart(),
                            chunk.pageEnd(),
                            chunk.text(),
                            chunk.sectionTitle(),
                            chunk.chunkOrder()
                    ))
                    .toList();
            Path textPath = workspaceManager.resolveWorkspacePath(
                    USER_ID,
                    WORKSPACE_ID,
                    WorkspaceArea.WORKSPACE,
                    "documents/%s/text/fulltext.txt".formatted(uploaded.documentId())
            );
            Files.createDirectories(textPath.getParent());
            Files.writeString(textPath, normalizedChunks.stream().map(DocumentChunk::text).reduce("", (left, right) -> left + right));
            var textArtifact = artifactService.register(new RegisterArtifactCommand(
                    USER_ID,
                    WORKSPACE_ID,
                    null,
                    name + ".txt",
                    ArtifactType.EXTRACTED_TEXT,
                    ArtifactVisibility.USER_VISIBLE,
                    WorkspaceArea.WORKSPACE,
                    "documents/%s/text/fulltext.txt".formatted(uploaded.documentId()),
                    "text/plain"
            ));

            Path chunkPath = workspaceManager.resolveWorkspacePath(
                    USER_ID,
                    WORKSPACE_ID,
                    WorkspaceArea.WORKSPACE,
                    "documents/%s/chunks.json".formatted(uploaded.documentId())
            );
            Files.createDirectories(chunkPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(chunkPath.toFile(), normalizedChunks);
            var chunkArtifact = artifactService.register(new RegisterArtifactCommand(
                    USER_ID,
                    WORKSPACE_ID,
                    null,
                    name + ".chunks.json",
                    ArtifactType.CHUNK_INDEX,
                    ArtifactVisibility.INTERNAL,
                    WorkspaceArea.WORKSPACE,
                    "documents/%s/chunks.json".formatted(uploaded.documentId()),
                    "application/json"
            ));

            Path manifestPath = workspaceManager.resolveWorkspacePath(
                    USER_ID,
                    WORKSPACE_ID,
                    WorkspaceArea.WORKSPACE,
                    "documents/%s/manifest.json".formatted(uploaded.documentId())
            );
            Files.createDirectories(manifestPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), objectMapper.createObjectNode()
                    .put("documentId", uploaded.documentId())
                    .put("name", name)
                    .put("kind", "pdf")
                    .put("pageCount", normalizedChunks.stream().mapToInt(DocumentChunk::pageEnd).max().orElse(1))
                    .put("textArtifactId", textArtifact.artifactId())
                    .put("chunkArtifactId", chunkArtifact.artifactId())
                    .put("chunkStrategy", "semantic-v1")
                    .put("chunkCount", normalizedChunks.size()));

            return documentStore.markReady(
                    USER_ID,
                    WORKSPACE_ID,
                    uploaded.documentId(),
                    textArtifact.artifactId(),
                    chunkArtifact.artifactId()
            );
        }
    }
}
