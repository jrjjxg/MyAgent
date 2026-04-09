package com.xg.platform.api.controller;

import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.api.upload.ChunkedUploadService;
import com.xg.platform.contracts.artifact.CompleteUploadSessionResponse;
import com.xg.platform.contracts.artifact.CreateUploadSessionRequest;
import com.xg.platform.contracts.artifact.UploadResponse;
import com.xg.platform.contracts.artifact.UploadSessionStatusResponse;
import com.xg.platform.runtime.ThreadRuntimeService;
import com.xg.platform.runtime.WorkspaceRuntimeService;
import com.xg.platform.workspace.UploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
public class UploadController {

    private final ThreadRuntimeService threadRuntimeService;
    private final WorkspaceRuntimeService workspaceRuntimeService;
    private final UploadService uploadService;
    private final DocumentIngestService documentIngestService;
    private final ChunkedUploadService chunkedUploadService;

    public UploadController(ThreadRuntimeService threadRuntimeService,
                            WorkspaceRuntimeService workspaceRuntimeService,
                            UploadService uploadService,
                            DocumentIngestService documentIngestService,
                            ChunkedUploadService chunkedUploadService) {
        this.threadRuntimeService = threadRuntimeService;
        this.workspaceRuntimeService = workspaceRuntimeService;
        this.uploadService = uploadService;
        this.documentIngestService = documentIngestService;
        this.chunkedUploadService = chunkedUploadService;
    }

    @PostMapping(path = "/threads/{threadId}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@CurrentUserId String userId,
                                 @PathVariable String threadId,
                                 @RequestPart("file") MultipartFile file) {
        var thread = threadRuntimeService.getThread(userId, threadId);
        try (InputStream inputStream = file.getInputStream()) {
            var artifact = uploadService.uploadToWorkspace(
                    userId,
                    thread.workspaceId(),
                    threadId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    inputStream
            );
            var ticket = documentIngestService.scheduleIngestion(userId, thread.workspaceId(), threadId, artifact);
            threadRuntimeService.touchThread(userId, threadId);
            return new UploadResponse(artifact, ticket.documentId(), ticket.ingestTaskId());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read upload body", exception);
        }
    }

    @PostMapping(path = "/workspaces/{workspaceId}/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse uploadToWorkspace(@CurrentUserId String userId,
                                            @PathVariable String workspaceId,
                                            @RequestPart("file") MultipartFile file) {
        workspaceRuntimeService.getWorkspace(userId, workspaceId);
        try (InputStream inputStream = file.getInputStream()) {
            var artifact = uploadService.uploadToWorkspace(
                    userId,
                    workspaceId,
                    null,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    inputStream
            );
            var ticket = documentIngestService.scheduleIngestion(userId, workspaceId, null, artifact);
            return new UploadResponse(artifact, ticket.documentId(), ticket.ingestTaskId());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read upload body", exception);
        }
    }

    @PostMapping(path = "/threads/{threadId}/uploads/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadSessionStatusResponse createUploadSession(@CurrentUserId String userId,
                                                           @PathVariable String threadId,
                                                           @RequestBody CreateUploadSessionRequest request) {
        UploadSessionStatusResponse response = chunkedUploadService.createSession(userId, threadId, request);
        threadRuntimeService.touchThread(userId, threadId);
        return response;
    }

    @PostMapping(path = "/workspaces/{workspaceId}/uploads/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadSessionStatusResponse createWorkspaceUploadSession(@CurrentUserId String userId,
                                                                    @PathVariable String workspaceId,
                                                                    @RequestBody CreateUploadSessionRequest request) {
        return chunkedUploadService.createSession(userId, workspaceId, null, request);
    }

    @GetMapping("/threads/{threadId}/uploads/sessions/{uploadId}")
    public UploadSessionStatusResponse getUploadSession(@CurrentUserId String userId,
                                                        @PathVariable String threadId,
                                                        @PathVariable String uploadId) {
        return chunkedUploadService.getSessionStatus(userId, threadId, uploadId);
    }

    @GetMapping("/workspaces/{workspaceId}/uploads/sessions/{uploadId}")
    public UploadSessionStatusResponse getWorkspaceUploadSession(@CurrentUserId String userId,
                                                                 @PathVariable String workspaceId,
                                                                 @PathVariable String uploadId) {
        return chunkedUploadService.getSessionStatusByWorkspace(userId, workspaceId, uploadId);
    }

    @PutMapping(path = "/threads/{threadId}/uploads/sessions/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadSessionStatusResponse uploadChunk(@CurrentUserId String userId,
                                                   @PathVariable String threadId,
                                                   @PathVariable String uploadId,
                                                   @PathVariable int chunkIndex,
                                                   @RequestBody byte[] chunkPayload) {
        UploadSessionStatusResponse response = chunkedUploadService.uploadChunk(
                userId,
                threadId,
                uploadId,
                chunkIndex,
                chunkPayload
        );
        threadRuntimeService.touchThread(userId, threadId);
        return response;
    }

    @PutMapping(path = "/workspaces/{workspaceId}/uploads/sessions/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadSessionStatusResponse uploadWorkspaceChunk(@CurrentUserId String userId,
                                                            @PathVariable String workspaceId,
                                                            @PathVariable String uploadId,
                                                            @PathVariable int chunkIndex,
                                                            @RequestBody byte[] chunkPayload) {
        return chunkedUploadService.uploadChunkByWorkspace(userId, workspaceId, uploadId, chunkIndex, chunkPayload);
    }

    @PostMapping("/threads/{threadId}/uploads/sessions/{uploadId}/complete")
    public CompleteUploadSessionResponse completeUploadSession(@CurrentUserId String userId,
                                                               @PathVariable String threadId,
                                                               @PathVariable String uploadId) {
        CompleteUploadSessionResponse response = chunkedUploadService.completeSession(userId, threadId, uploadId);
        threadRuntimeService.touchThread(userId, threadId);
        return response;
    }

    @PostMapping("/workspaces/{workspaceId}/uploads/sessions/{uploadId}/complete")
    public CompleteUploadSessionResponse completeWorkspaceUploadSession(@CurrentUserId String userId,
                                                                        @PathVariable String workspaceId,
                                                                        @PathVariable String uploadId) {
        return chunkedUploadService.completeSessionByWorkspace(userId, workspaceId, uploadId);
    }
}
