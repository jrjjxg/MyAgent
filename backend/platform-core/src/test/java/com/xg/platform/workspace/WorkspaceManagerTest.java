package com.xg.platform.workspace;

import com.xg.platform.contracts.workspace.WorkspaceArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void ensureThreadWorkspaceCreatesExpectedDirectories() {
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);

        ThreadWorkspace threadWorkspace = workspaceManager.ensureThreadWorkspace("user-1", "thread-1");

        assertThat(threadWorkspace.root()).isEqualTo(tempDir.resolve("users").resolve("user-1").resolve("threads").resolve("thread-1"));
        assertThat(Files.isDirectory(threadWorkspace.uploads())).isTrue();
        assertThat(Files.isDirectory(threadWorkspace.workspace())).isTrue();
        assertThat(Files.isDirectory(threadWorkspace.outputs())).isTrue();
    }

    @Test
    void resolvePathRejectsTraversalOutsideWorkspaceArea() {
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);
        workspaceManager.ensureThreadWorkspace("user-1", "thread-1");

        assertThatThrownBy(() -> workspaceManager.resolvePath("user-1", "thread-1", WorkspaceArea.WORKSPACE, "..\\..\\secret.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ensureThreadWorkspaceRejectsInvalidIdentifiers() {
        WorkspaceManager workspaceManager = new WorkspaceManager(tempDir);

        assertThatThrownBy(() -> workspaceManager.ensureThreadWorkspace("../admin", "thread-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
