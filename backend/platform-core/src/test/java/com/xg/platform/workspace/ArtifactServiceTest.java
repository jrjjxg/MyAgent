package com.xg.platform.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.agent.core.test.InMemoryRuntimeSupport;
import com.xg.platform.contracts.workspace.ArtifactRecord;
import com.xg.platform.contracts.workspace.ArtifactType;
import com.xg.platform.contracts.workspace.ArtifactVisibility;
import com.xg.platform.contracts.workspace.RegisterArtifactCommand;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.workspace.application.ThreadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.WorkspaceManager;
import com.xg.platform.workspace.domain.ThreadWorkspace;

class ArtifactServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void registerArtifactPersistsAndListsMetadata() throws IOException {
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadService threadRuntimeService = new ThreadService(new InMemoryRuntimeSupport.InMemoryThreadRepository());
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        String threadId = threadRuntimeService.createThread("user-1", "workspace-1", "Thread").threadId();

        ThreadWorkspace threadWorkspace = workspaceManager.ensureThreadWorkspace("user-1", threadId);
        Files.writeString(threadWorkspace.outputs().resolve("report.md"), "# report");

        ArtifactRecord artifact = artifactService.register(new RegisterArtifactCommand(
                "user-1",
                "workspace-1",
                threadId,
                "Final report",
                ArtifactType.REPORT,
                ArtifactVisibility.USER_VISIBLE,
                WorkspaceArea.OUTPUTS,
                "report.md",
                "text/markdown"
        ));

        List<ArtifactRecord> artifacts = artifactService.listArtifacts("user-1", threadId);

        assertThat(artifacts).singleElement().isEqualTo(artifact);
        assertThat(artifact.sizeBytes()).isGreaterThan(0);
        assertThat(artifact.relativePath()).isEqualTo("report.md");
    }

    @Test
    void registerArtifactRequiresExistingFile() {
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ThreadService threadRuntimeService = new ThreadService(new InMemoryRuntimeSupport.InMemoryThreadRepository());
        ArtifactService artifactService = new ArtifactService(workspaceManager, threadRuntimeService, objectMapper);
        String threadId = threadRuntimeService.createThread("user-1", "workspace-1", "Thread").threadId();

        workspaceManager.ensureThreadWorkspace("user-1", threadId);

        assertThatThrownBy(() -> artifactService.register(new RegisterArtifactCommand(
                "user-1",
                "workspace-1",
                threadId,
                "Missing report",
                ArtifactType.REPORT,
                ArtifactVisibility.USER_VISIBLE,
                WorkspaceArea.OUTPUTS,
                "missing.md",
                "text/markdown"
        ))).isInstanceOf(RuntimeException.class);
    }
}
