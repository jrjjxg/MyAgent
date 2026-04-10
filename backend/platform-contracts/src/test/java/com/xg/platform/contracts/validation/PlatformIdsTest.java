package com.xg.platform.contracts.validation;

import com.xg.platform.contracts.shared.validation.PlatformIds;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformIdsTest {

    @Test
    void acceptsSafeIdentifiersAndRelativePaths() {
        assertThat(PlatformIds.requireUserId("user-1")).isEqualTo("user-1");
        assertThat(PlatformIds.requireThreadId("thread_01")).isEqualTo("thread_01");
        assertThat(PlatformIds.requireRelativePath("reports/final.md", "relativePath"))
                .hasToString("reports\\final.md");
    }

    @Test
    void rejectsUnsafeValues() {
        assertThatThrownBy(() -> PlatformIds.requireUserId("../admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlatformIds.requireRelativePath("../secret.txt", "relativePath"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
