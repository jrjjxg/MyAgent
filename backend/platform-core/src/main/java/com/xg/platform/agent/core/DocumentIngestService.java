package com.xg.platform.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.contracts.artifact.ArtifactVisibility;
import com.xg.platform.contracts.artifact.RegisterArtifactCommand;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.document.DocumentStatus;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.message.RunEventType;
import com.xg.platform.contracts.task.TaskKind;
import com.xg.platform.contracts.task.TaskRecord;
import com.xg.platform.memory.ChunkIndexStore;
import com.xg.platform.memory.DocumentChunk;
import com.xg.platform.memory.DocumentStore;
import com.xg.platform.memory.SemanticChunker;
import com.xg.platform.runtime.RunEventRepository;
import com.xg.platform.runtime.RetryableTaskException;
import com.xg.platform.runtime.TaskDispatchRequest;
import com.xg.platform.runtime.TaskDispatcher;
import com.xg.platform.runtime.TaskRepository;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.workspace.ArtifactService;
import com.xg.platform.workspace.WorkspaceManager;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.tools.CliToolExecutor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocumentIngestService {

    private static final Logger logger = Logger.getLogger(DocumentIngestService.class.getName());

    private final DocumentStore documentStore;
    private final ChunkIndexStore chunkIndexStore;
    private final ArtifactService artifactService;
    private final WorkspaceManager workspaceManager;
    private final TaskRepository taskStore;
    private final RunEventRepository runEventStore;
    private final ThreadRuntimeService threadRuntimeService;
    private final CliToolExecutor cliToolExecutor;
    private final ObjectMapper objectMapper;
    private final TaskDispatcher taskDispatcher;
    private final SemanticChunker semanticChunker;
    private final int maxAttempts;
    private final Duration staleRunningWindow;

    public DocumentIngestService(DocumentStore documentStore,
                                 ChunkIndexStore chunkIndexStore,
                                 ArtifactService artifactService,
                                 WorkspaceManager workspaceManager,
                                 TaskRepository taskStore,
                                 RunEventRepository runEventStore,
                                 ThreadRuntimeService threadRuntimeService,
                                 CliToolExecutor cliToolExecutor,
                                 ObjectMapper objectMapper,
                                 TaskDispatcher taskDispatcher,
                                 SemanticChunker semanticChunker) {
        this(
                documentStore,
                chunkIndexStore,
                artifactService,
                workspaceManager,
                taskStore,
                runEventStore,
                threadRuntimeService,
                cliToolExecutor,
                objectMapper,
                taskDispatcher,
                semanticChunker,
                3,
                15
        );
    }

    public DocumentIngestService(DocumentStore documentStore,
                                 ChunkIndexStore chunkIndexStore,
                                 ArtifactService artifactService,
                                 WorkspaceManager workspaceManager,
                                 TaskRepository taskStore,
                                 RunEventRepository runEventStore,
                                 ThreadRuntimeService threadRuntimeService,
                                 CliToolExecutor cliToolExecutor,
                                 ObjectMapper objectMapper,
                                 TaskDispatcher taskDispatcher,
                                 SemanticChunker semanticChunker,
                                 int maxAttempts,
                                 long staleRunningMinutes) {
        this.documentStore = documentStore;
        this.chunkIndexStore = chunkIndexStore;
        this.artifactService = artifactService;
        this.workspaceManager = workspaceManager;
        this.taskStore = taskStore;
        this.runEventStore = runEventStore;
        this.threadRuntimeService = threadRuntimeService;
        this.cliToolExecutor = cliToolExecutor;
        this.objectMapper = objectMapper;
        this.taskDispatcher = taskDispatcher;
        this.semanticChunker = semanticChunker;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.staleRunningWindow = Duration.ofMinutes(Math.max(1L, staleRunningMinutes));
    }

    public DocumentIngestionTicket scheduleIngestion(String userId, String threadId, ArtifactRecord uploadArtifact) {
        String workspaceId = threadRuntimeService.getThread(userId, threadId).workspaceId();
        return scheduleIngestion(userId, workspaceId, threadId, uploadArtifact);
    }

    public DocumentIngestionTicket scheduleIngestion(String userId,
                                                     String workspaceId,
                                                     String sourceThreadId,
                                                     ArtifactRecord uploadArtifact) {
        if (!isSupported(uploadArtifact.name())) {
            logger.info(() -> "document ingest skipped unsupported file"
                    + " workspace=" + workspaceId
                    + " thread=" + sourceThreadId
                    + " artifact=" + uploadArtifact.artifactId()
                    + " name=" + uploadArtifact.name());
            return new DocumentIngestionTicket(null, null);
        }
        DocumentRecord documentRecord = findOrCreateDocument(userId, workspaceId, sourceThreadId, uploadArtifact);
        TaskRecord existingTask = taskStore.findIngestTaskByDocument(userId, workspaceId, documentRecord.documentId()).orElse(null);
        if (documentRecord.status() == DocumentStatus.READY) {
            logger.info(() -> "document ingest already ready"
                    + " workspace=" + workspaceId
                    + " thread=" + sourceThreadId
                    + " document=" + documentRecord.documentId()
                    + " task=" + (existingTask == null ? null : existingTask.taskId())
                    + " name=" + uploadArtifact.name());
            return new DocumentIngestionTicket(documentRecord.documentId(), existingTask == null ? null : existingTask.taskId());
        }
        if (existingTask != null) {
            if (existingTask.status() == com.xg.platform.contracts.task.TaskStatus.FAILED
                    || existingTask.status() == com.xg.platform.contracts.task.TaskStatus.CANCELLED
                    || existingTask.status() == com.xg.platform.contracts.task.TaskStatus.COMPLETED) {
                logger.info(() -> "document ingest resetting existing task"
                        + " workspace=" + workspaceId
                        + " thread=" + sourceThreadId
                        + " document=" + documentRecord.documentId()
                        + " task=" + existingTask.taskId()
                        + " previousStatus=" + existingTask.status());
                TaskRecord reset = taskStore.resetTaskToQueued(
                        userId,
                        existingTask.taskId(),
                        "Queued document ingestion",
                        "queued",
                        0
                );
                dispatchIngestion(userId, workspaceId, sourceThreadId, reset.taskId(), documentRecord.documentId());
                return new DocumentIngestionTicket(documentRecord.documentId(), reset.taskId());
            }
            logger.info(() -> "document ingest reusing queued task"
                    + " workspace=" + workspaceId
                    + " thread=" + sourceThreadId
                    + " document=" + documentRecord.documentId()
                    + " task=" + existingTask.taskId()
                    + " status=" + existingTask.status());
            return new DocumentIngestionTicket(documentRecord.documentId(), existingTask.taskId());
        }
        String taskId = UUID.randomUUID().toString();
        Optional<TaskRecord> inserted = taskStore.createQueuedIngestTaskIfAbsent(
                userId,
                workspaceId,
                sourceThreadId,
                documentRecord.documentId(),
                taskId,
                uploadArtifact.name(),
                "Queued document ingestion",
                maxAttempts
        );
        TaskRecord task = inserted.orElseGet(() -> taskStore.findIngestTaskByDocument(userId, workspaceId, documentRecord.documentId())
                .orElseThrow(() -> new IllegalStateException("Failed to create or locate ingest task")));
        if (inserted.isPresent()) {
            logger.info(() -> "document ingest queued new task"
                    + " workspace=" + workspaceId
                    + " thread=" + sourceThreadId
                    + " document=" + documentRecord.documentId()
                    + " task=" + task.taskId()
                    + " name=" + uploadArtifact.name());
            dispatchIngestion(userId, workspaceId, sourceThreadId, task.taskId(), documentRecord.documentId());
        } else {
            logger.info(() -> "document ingest found concurrent existing task"
                    + " workspace=" + workspaceId
                    + " thread=" + sourceThreadId
                    + " document=" + documentRecord.documentId()
                    + " task=" + task.taskId()
                    + " status=" + task.status());
        }
        return new DocumentIngestionTicket(documentRecord.documentId(), task.taskId());
    }

    public void process(TaskDispatchRequest request) {
        if (request.taskKind() != TaskKind.INGEST) {
            return;
        }
        IngestionTaskPayload payload = deserializeIngestionTask(request.taskInput());
        String workspaceId = payload.workspaceId() == null || payload.workspaceId().isBlank()
                ? request.workspaceId()
                : payload.workspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalStateException("Ingest task is missing workspaceId");
        }
        Optional<TaskRecord> claimed = taskStore.claimQueuedOrStaleRunningTask(
                request.userId(),
                request.taskId(),
                Instant.now().minus(staleRunningWindow),
                "Ingesting document",
                "ingest",
                50
        );
        if (claimed.isEmpty()) {
            logger.info(() -> "document ingest skipped task claim"
                    + " workspace=" + workspaceId
                    + " thread=" + request.threadId()
                    + " document=" + payload.documentId()
                    + " task=" + request.taskId());
            return;
        }
        logger.info(() -> "document ingest claimed task"
                + " workspace=" + workspaceId
                + " thread=" + request.threadId()
                + " document=" + payload.documentId()
                + " task=" + request.taskId()
                + " attempt=" + claimed.get().attemptCount()
                + "/" + claimed.get().maxAttempts());
        runAsyncIngestion(
                request.userId(),
                request.threadId(),
                request.taskId(),
                workspaceId,
                payload.documentId()
        );
    }

    public List<DocumentRecord> ensureReadyDocuments(String userId,
                                                     String threadId,
                                                     List<DocumentRecord> documents,
                                                     String runId,
                                                     AgentOutputEmitter outputEmitter) {
        List<DocumentRecord> ready = new ArrayList<>();
        for (DocumentRecord document : documents) {
            if (document.status() == DocumentStatus.READY || document.status() == DocumentStatus.FAILED) {
                ready.add(document);
                continue;
            }
            ready.add(runInlineIngestion(userId, threadId, document.workspaceId(), document.documentId(), runId, outputEmitter));
        }
        return ready;
    }

    public List<DocumentRecord> listDocuments(String userId, String threadId) {
        return documentStore.listDocuments(userId, threadId);
    }

    public List<DocumentRecord> listDocumentsByWorkspace(String userId, String workspaceId) {
        return documentStore.listDocumentsByWorkspace(userId, workspaceId);
    }

    private void runAsyncIngestion(String userId, String threadId, String taskId, String workspaceId, String documentId) {
        logger.info(() -> "document ingest started"
                + " workspace=" + workspaceId
                + " thread=" + threadId
                + " document=" + documentId
                + " task=" + taskId);
        publishEvent(userId, threadId, taskId, RunEventType.RUN_STARTED, Map.of("taskId", taskId, "kind", TaskKind.INGEST.name()), null);
        try {
            runInlineIngestion(userId, threadId, workspaceId, documentId, taskId, new AgentOutputEmitter() {
                @Override
                public void emitText(String delta) {
                }
            });
            taskStore.markCompleted(userId, taskId, "Document ingested", null);
            logger.info(() -> "document ingest completed"
                    + " workspace=" + workspaceId
                    + " thread=" + threadId
                    + " document=" + documentId
                    + " task=" + taskId);
            publishEvent(userId, threadId, taskId, RunEventType.RUN_COMPLETED, Map.of("taskId", taskId, "kind", TaskKind.INGEST.name()), null);
        } catch (RuntimeException exception) {
            TaskRecord task = taskStore.findTaskById(userId, taskId).orElse(null);
            boolean terminal = task == null || task.attemptCount() + 1 >= task.maxAttempts();
            if (terminal) {
                taskStore.markFailed(
                        userId,
                        taskId,
                        safeMessage(exception),
                        "failed",
                        task == null ? null : task.progress(),
                        safeMessage(exception),
                        true
                );
                documentStore.markFailed(userId, workspaceId, documentId);
                logger.log(Level.WARNING,
                        "document ingest failed terminal"
                                + " workspace=" + workspaceId
                                + " thread=" + threadId
                                + " document=" + documentId
                                + " task=" + taskId
                                + " error=" + safeMessage(exception),
                        exception);
                publishEvent(userId, threadId, taskId, RunEventType.RUN_FAILED, Map.of("error", safeMessage(exception)), null);
            } else {
                taskStore.markFailed(
                        userId,
                        taskId,
                        "Retrying document ingestion",
                        "retrying",
                        0,
                        safeMessage(exception),
                        false
                );
                documentStore.markUploaded(userId, workspaceId, documentId);
                logger.log(Level.WARNING,
                        "document ingest failed retryable"
                                + " workspace=" + workspaceId
                                + " thread=" + threadId
                                + " document=" + documentId
                                + " task=" + taskId
                                + " nextAttempt=" + (task == null ? 1 : task.attemptCount() + 1)
                                + "/" + (task == null ? maxAttempts : task.maxAttempts())
                                + " error=" + safeMessage(exception),
                        exception);
                throw new RetryableTaskException("Retry ingest task: " + taskId, exception);
            }
        } finally {
            if (hasThread(threadId)) {
                threadRuntimeService.touchThread(userId, threadId);
            }
        }
    }

    private void dispatchIngestion(String userId,
                                   String workspaceId,
                                   String sourceThreadId,
                                   String taskId,
                                   String documentId) {
        logger.info(() -> "document ingest dispatched"
                + " mode=" + taskDispatcher.getClass().getSimpleName()
                + " workspace=" + workspaceId
                + " thread=" + sourceThreadId
                + " document=" + documentId
                + " task=" + taskId);
        taskDispatcher.dispatch(new TaskDispatchRequest(
                userId,
                sourceThreadId,
                taskId,
                TaskKind.INGEST,
                null,
                serializeIngestionTask(workspaceId, documentId),
                workspaceId
        ));
    }

    private String serializeIngestionTask(String workspaceId, String documentId) {
        try {
            return objectMapper.writeValueAsString(new IngestionTaskPayload(workspaceId, documentId));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize ingestion task payload", exception);
        }
    }

    private IngestionTaskPayload deserializeIngestionTask(String taskInput) {
        try {
            return objectMapper.readValue(taskInput, IngestionTaskPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize ingestion task payload", exception);
        }
    }

    private DocumentRecord runInlineIngestion(String userId,
                                              String threadId,
                                              String workspaceId,
                                              String documentId,
                                              String runId,
                                              AgentOutputEmitter outputEmitter) {
        DocumentRecord current = documentStore.findById(userId, workspaceId, documentId);
        if (hasReadyArtifacts(userId, current)) {
            logger.info(() -> "document ingest reused ready artifacts"
                    + " workspace=" + workspaceId
                    + " thread=" + threadId
                    + " document=" + documentId
                    + " textArtifact=" + current.primaryTextArtifactId()
                    + " chunkArtifact=" + current.chunkIndexArtifactId());
            return current;
        }
        try {
            ArtifactRecord sourceArtifact = artifactService.findArtifactByWorkspace(userId, workspaceId, current.sourceArtifactId());
            Path sourcePath = artifactService.resolveArtifactPath(userId, sourceArtifact);
            documentStore.markIngesting(userId, workspaceId, documentId);
            logger.info(() -> "document ingest resolving source"
                    + " workspace=" + workspaceId
                    + " thread=" + threadId
                    + " document=" + documentId
                    + " run=" + runId
                    + " sourceArtifact=" + sourceArtifact.artifactId()
                    + " name=" + current.name()
                    + " path=" + sourcePath);

            publishEvent(userId, threadId, runId, RunEventType.STAGE_STARTED, Map.of("stage", "ensure-ingest", "documentId", documentId), outputEmitter);
            publishEvent(userId, threadId, runId, RunEventType.TOOL_STARTED, Map.of("toolName", "extract_document", "documentId", documentId), outputEmitter);
            logger.info(() -> "document ingest invoking extract_document"
                    + " workspace=" + workspaceId
                    + " document=" + documentId
                    + " path=" + sourcePath);
            ExtractDocumentResponse response = cliToolExecutor.execute(
                    "extract_document",
                    new ExtractDocumentRequest(sourcePath.toString()),
                    ExtractDocumentResponse.class
            );
            logger.info(() -> "document ingest extracted document"
                    + " workspace=" + workspaceId
                    + " document=" + documentId
                    + " kind=" + response.kind()
                    + " pageCount=" + response.pageCount()
                    + " fullTextChars=" + (response.fullText() == null ? 0 : response.fullText().length()));
            publishEvent(userId, threadId, runId, RunEventType.TOOL_COMPLETED, Map.of("toolName", "extract_document", "documentId", documentId), outputEmitter);

            String basePath = "documents/%s".formatted(documentId);
            Path textPath = writeWorkspaceFile(userId, workspaceId, basePath + "/text/fulltext.txt", response.fullText());
            String textRelativePath = workspaceManager.workspaceAreaRoot(userId, workspaceId, WorkspaceArea.WORKSPACE)
                    .relativize(textPath)
                    .toString()
                    .replace('\\', '/');
            ArtifactRecord textArtifact = findOrRegisterWorkspaceArtifact(
                    userId,
                    workspaceId,
                    current.name() + ".txt",
                    ArtifactType.EXTRACTED_TEXT,
                    ArtifactVisibility.USER_VISIBLE,
                    textRelativePath,
                    "text/plain"
            );
            publishEvent(userId, threadId, runId, RunEventType.ARTIFACT_CREATED, textArtifact, outputEmitter);

            List<DocumentChunk> chunks = buildChunks(current.documentId(), current.name(), response.pages());
            logger.info(() -> "document ingest chunked document"
                    + " workspace=" + workspaceId
                    + " document=" + documentId
                    + " chunkCount=" + chunks.size());
            Path chunkPath = chunkIndexStore.writeChunkIndex(userId, workspaceId, basePath + "/chunks.json", chunks);
            String chunkRelativePath = workspaceManager.workspaceAreaRoot(userId, workspaceId, WorkspaceArea.WORKSPACE)
                    .relativize(chunkPath)
                    .toString()
                    .replace('\\', '/');
            ArtifactRecord chunkArtifact = findOrRegisterWorkspaceArtifact(
                    userId,
                    workspaceId,
                    current.name() + ".chunks.json",
                    ArtifactType.CHUNK_INDEX,
                    ArtifactVisibility.INTERNAL,
                    chunkRelativePath,
                    "application/json"
            );

            writeManifest(userId, workspaceId, documentId, current, response, textArtifact, chunkArtifact, chunks);

            if ("pdf".equalsIgnoreCase(response.kind())) {
                Path pagesDir = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.WORKSPACE, basePath + "/pages");
                publishEvent(userId, threadId, runId, RunEventType.TOOL_STARTED, Map.of("toolName", "render_pdf_pages", "documentId", documentId), outputEmitter);
                logger.info(() -> "document ingest invoking render_pdf_pages"
                        + " workspace=" + workspaceId
                        + " document=" + documentId
                        + " input=" + sourcePath
                        + " outputDir=" + pagesDir);
                cliToolExecutor.execute(
                        "render_pdf_pages",
                        new RenderPdfPagesRequest(sourcePath.toString(), pagesDir.toString()),
                        RenderPdfPagesResponse.class
                );
                logger.info(() -> "document ingest rendered pdf pages"
                        + " workspace=" + workspaceId
                        + " document=" + documentId
                        + " outputDir=" + pagesDir);
                publishEvent(userId, threadId, runId, RunEventType.TOOL_COMPLETED, Map.of("toolName", "render_pdf_pages", "documentId", documentId), outputEmitter);
            }

            DocumentRecord ready = documentStore.markReady(userId, workspaceId, documentId, textArtifact.artifactId(), chunkArtifact.artifactId());
            logger.info(() -> "document ingest marked ready"
                    + " workspace=" + workspaceId
                    + " document=" + documentId
                    + " textArtifact=" + textArtifact.artifactId()
                    + " chunkArtifact=" + chunkArtifact.artifactId());
            publishEvent(userId, threadId, runId, RunEventType.STAGE_COMPLETED, Map.of("stage", "ensure-ingest", "documentId", documentId), outputEmitter);
            return ready;
        } catch (RuntimeException exception) {
            documentStore.markFailed(userId, workspaceId, documentId);
            logger.log(Level.WARNING,
                    "document ingest inline failure"
                            + " workspace=" + workspaceId
                            + " thread=" + threadId
                            + " document=" + documentId
                            + " run=" + runId
                            + " error=" + safeMessage(exception),
                    exception);
            publishEvent(userId, threadId, runId, RunEventType.TOOL_FAILED, Map.of("stage", "ensure-ingest", "documentId", documentId, "error", safeMessage(exception)), outputEmitter);
            throw exception;
        }
    }

    private DocumentRecord findOrCreateDocument(String userId, String workspaceId, String sourceThreadId, ArtifactRecord uploadArtifact) {
        try {
            DocumentRecord existing = documentStore.findBySourceArtifactId(userId, workspaceId, uploadArtifact.artifactId());
            logger.info(() -> "document ingest found existing document"
                    + " workspace=" + workspaceId
                    + " thread=" + sourceThreadId
                    + " document=" + existing.documentId()
                    + " artifact=" + uploadArtifact.artifactId()
                    + " name=" + uploadArtifact.name()
                    + " status=" + existing.status());
            return existing;
        } catch (NoSuchElementException ignored) {
            DocumentRecord created = documentStore.createUploaded(
                    userId,
                    workspaceId,
                    sourceThreadId,
                    uploadArtifact.artifactId(),
                    uploadArtifact.name()
            );
            logger.info(() -> "document ingest created document"
                    + " workspace=" + workspaceId
                    + " thread=" + sourceThreadId
                    + " document=" + created.documentId()
                    + " artifact=" + uploadArtifact.artifactId()
                    + " name=" + uploadArtifact.name());
            return created;
        }
    }

    private ArtifactRecord findOrRegisterWorkspaceArtifact(String userId,
                                                           String workspaceId,
                                                           String name,
                                                           ArtifactType artifactType,
                                                           ArtifactVisibility visibility,
                                                           String relativePath,
                                                           String contentType) {
        return artifactService.findArtifactByWorkspacePath(userId, workspaceId, WorkspaceArea.WORKSPACE, relativePath)
                .orElseGet(() -> artifactService.register(new RegisterArtifactCommand(
                        userId,
                        workspaceId,
                        null,
                        name,
                        artifactType,
                        visibility,
                        WorkspaceArea.WORKSPACE,
                        relativePath,
                        contentType
                )));
    }

    private boolean hasReadyArtifacts(String userId, DocumentRecord documentRecord) {
        if (documentRecord.status() != DocumentStatus.READY
                || documentRecord.primaryTextArtifactId() == null
                || documentRecord.chunkIndexArtifactId() == null) {
            return false;
        }
        try {
            artifactService.findArtifactByWorkspace(userId, documentRecord.workspaceId(), documentRecord.primaryTextArtifactId());
            artifactService.findArtifactByWorkspace(userId, documentRecord.workspaceId(), documentRecord.chunkIndexArtifactId());
            return true;
        } catch (NoSuchElementException ignored) {
            return false;
        }
    }

    private Path writeWorkspaceFile(String userId, String workspaceId, String relativePath, String content) {
        Path path = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.WORKSPACE, relativePath);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            return path;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write workspace file", exception);
        }
    }

    private void writeManifest(String userId,
                               String workspaceId,
                               String documentId,
                               DocumentRecord current,
                               ExtractDocumentResponse response,
                               ArtifactRecord textArtifact,
                               ArtifactRecord chunkArtifact,
                               List<DocumentChunk> chunks) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("documentId", documentId);
        manifest.put("sourceArtifactId", current.sourceArtifactId());
        manifest.put("name", current.name());
        manifest.put("kind", response.kind());
        manifest.put("pageCount", response.pageCount());
        manifest.put("textArtifactId", textArtifact.artifactId());
        manifest.put("chunkArtifactId", chunkArtifact.artifactId());
        manifest.put("chunkStrategy", SemanticChunker.STRATEGY);
        manifest.put("chunkCount", chunks == null ? 0 : chunks.size());
        manifest.put("targetChars", SemanticChunker.TARGET_CHARS);
        manifest.put("minChars", SemanticChunker.MIN_CHARS);
        manifest.put("maxChars", SemanticChunker.MAX_CHARS);
        manifest.put("overlapChars", SemanticChunker.OVERLAP_CHARS);
        Path manifestPath = workspaceManager.resolveWorkspacePath(userId, workspaceId, WorkspaceArea.WORKSPACE, "documents/%s/manifest.json".formatted(documentId));
        try {
            Files.createDirectories(manifestPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write document manifest", exception);
        }
    }

    private List<DocumentChunk> buildChunks(String documentId, String documentName, List<ExtractedPage> pages) {
        List<SemanticChunker.PageContent> pageContents = pages.stream()
                .map(page -> new SemanticChunker.PageContent(page.pageNumber(), page.text()))
                .toList();
        return semanticChunker.chunk(documentId, documentName, pageContents);
    }

    private boolean isSupported(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase();
        return normalized.endsWith(".pdf") || normalized.endsWith(".docx") || normalized.endsWith(".txt") || normalized.endsWith(".md");
    }

    private void publishEvent(String userId,
                              String threadId,
                              String runId,
                              RunEventType runEventType,
                              Object payload,
                              AgentOutputEmitter outputEmitter) {
        if (hasThread(threadId)) {
            RunEvent event = new RunEvent(runId, threadId, runEventType.value(), Instant.now(), payload);
            runEventStore.appendEvent(userId, threadId, event);
        }
        if (outputEmitter != null) {
            outputEmitter.emitEvent(runEventType, payload);
        }
    }

    private boolean hasThread(String threadId) {
        return threadId != null && !threadId.isBlank();
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    public record DocumentIngestionTicket(String documentId, String ingestTaskId) {
    }

    private record IngestionTaskPayload(String workspaceId, String documentId) {
    }

    private record ExtractDocumentRequest(String input_path) {
    }

    private record RenderPdfPagesRequest(String input_path, String output_dir) {
    }

    private record ExtractDocumentResponse(String kind, int pageCount, String fullText, List<ExtractedPage> pages) {
    }

    private record ExtractedPage(int pageNumber, String text) {
    }

    private record RenderPdfPagesResponse(List<String> files) {
    }
}
