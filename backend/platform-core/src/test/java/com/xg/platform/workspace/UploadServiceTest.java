package com.xg.platform.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport;
import com.xg.platform.contracts.artifact.ArtifactRecord;
import com.xg.platform.contracts.artifact.ArtifactType;
import com.xg.platform.runtime.ThreadRuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadStoresFileAndRegistersArtifact() throws Exception {
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadRuntimeService threadRuntimeService = new ThreadRuntimeService(new InMemoryRuntimeSupport.InMemoryThreadRepository());
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        UploadService uploadService = new UploadService(workspaceManager, artifactService, threadRuntimeService);
        String threadId = threadRuntimeService.createThread("user-1", "workspace-1", "Thread").threadId();

        ArtifactRecord artifact = uploadService.upload("user-1", threadId, "report draft.md", "text/markdown", "hello".getBytes());

        assertThat(artifact.type()).isEqualTo(ArtifactType.UPLOAD);
        assertThat(artifact.relativePath()).isEqualTo("report_draft.md");
        assertThat(Files.readString(workspaceManager.resolveWorkspacePath("user-1", "workspace-1", artifact.area(), artifact.relativePath())))
                .isEqualTo("hello");
    }

    @Test
    void uploadRejectsInvalidFilename() {
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadRuntimeService threadRuntimeService = new ThreadRuntimeService(new InMemoryRuntimeSupport.InMemoryThreadRepository());
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        UploadService uploadService = new UploadService(workspaceManager, artifactService, threadRuntimeService);
        String threadId = threadRuntimeService.createThread("user-1", "workspace-1", "Thread").threadId();

        assertThatThrownBy(() -> uploadService.upload("user-1", threadId, "...", "text/plain", "hello".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
