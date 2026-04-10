package com.xg.platform.agent.core.shared;

import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryStatus;
import com.xg.platform.contracts.memory.LongTermMemoryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryContextFormatterTest {

    @Test
    void includesOnlyCurrentThreadEpisodicMemories() {
        MemoryContextFormatter formatter = new MemoryContextFormatter();
        Instant now = Instant.parse("2026-04-03T00:00:00Z");
        List<LongTermMemoryRecord> memories = List.of(
                new LongTermMemoryRecord(
                        "profile-1",
                        "user-1",
                        LongTermMemoryType.PROFILE,
                        "profile.language",
                        "Language preference",
                        "User prefers Chinese responses.",
                        null,
                        "thread-other",
                        "message-1",
                        null,
                        LongTermMemoryStatus.ACTIVE,
                        now,
                        now
                ),
                new LongTermMemoryRecord(
                        "episode-1",
                        "user-1",
                        LongTermMemoryType.EPISODIC,
                        "episode.maven.wrapper",
                        "Maven wrapper issue",
                        "This thread already debugged Maven wrapper startup.",
                        null,
                        "thread-1",
                        "message-2",
                        null,
                        LongTermMemoryStatus.ACTIVE,
                        now,
                        now
                ),
                new LongTermMemoryRecord(
                        "episode-2",
                        "user-1",
                        LongTermMemoryType.EPISODIC,
                        "episode.pg.env",
                        "Postgres env issue",
                        "Another thread debugged datasource env vars.",
                        null,
                        "thread-2",
                        "message-3",
                        null,
                        LongTermMemoryStatus.ACTIVE,
                        now,
                        now
                )
        );

        String formatted = formatter.formatLongTermMemory(memories, "thread-1");

        assertThat(formatted).contains("Profile memories:");
        assertThat(formatted).doesNotContain("Procedural memories:");
        assertThat(formatted).contains("Current thread episodic memories:");
        assertThat(formatted).contains("Maven wrapper issue");
        assertThat(formatted).doesNotContain("Postgres env issue");
    }

    @Test
    void omitsEpisodicMemoriesWithoutThreadContext() {
        MemoryContextFormatter formatter = new MemoryContextFormatter();
        Instant now = Instant.parse("2026-04-03T00:00:00Z");
        List<LongTermMemoryRecord> memories = List.of(
                new LongTermMemoryRecord(
                        "episode-1",
                        "user-1",
                        LongTermMemoryType.EPISODIC,
                        "episode.maven.wrapper",
                        "Maven wrapper issue",
                        "This thread already debugged Maven wrapper startup.",
                        null,
                        "thread-1",
                        "message-2",
                        null,
                        LongTermMemoryStatus.ACTIVE,
                        now,
                        now
                )
        );

        String formatted = formatter.formatLongTermMemory(memories);

        assertThat(formatted).isEqualTo("- none");
    }

    @Test
    void includesProceduralMemoriesInOwnSection() {
        MemoryContextFormatter formatter = new MemoryContextFormatter();
        Instant now = Instant.parse("2026-04-03T00:00:00Z");
        List<LongTermMemoryRecord> memories = List.of(
                new LongTermMemoryRecord(
                        "procedure-1",
                        "user-1",
                        LongTermMemoryType.PROCEDURAL,
                        "procedure.answer_style",
                        "Answer style",
                        "Lead with the conclusion and keep implementation notes short.",
                        null,
                        null,
                        null,
                        null,
                        LongTermMemoryStatus.ACTIVE,
                        now,
                        now
                )
        );

        String formatted = formatter.formatLongTermMemory(memories, "thread-1");

        assertThat(formatted).contains("Procedural memories:");
        assertThat(formatted).contains("Answer style");
    }
}
