package com.xg.platform.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.contracts.document.DocumentRecord;
import com.xg.platform.contracts.document.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticChunkerTest {

    @Test
    void groupsParagraphsByHeadingAndUsesSectionTitlesDuringRetrieval() {
        SemanticChunker chunker = new SemanticChunker();
        List<DocumentChunk> chunks = chunker.chunk(
                "doc-1",
                "platform-notes.md",
                List.of(new SemanticChunker.PageContent(
                        1,
                        "# Overview\n\n"
                                + repeatSentence("The agent runtime coordinates chat, scoping, and research execution flows.", 12)
                                + "\n\nCaching Strategy\n\n"
                                + repeatSentence("Redis recent window cache keeps hot conversation context ready for reads.", 12)
                ))
        );
        ContextAssembler assembler = new ContextAssembler();
        DocumentRecord document = new DocumentRecord(
                "doc-1",
                "workspace-1",
                "thread-1",
                "artifact-1",
                "platform-notes.md",
                DocumentStatus.READY,
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        List<RetrievedChunk> matches = assembler.retrieve(
                "caching",
                List.of(document),
                ignored -> chunks,
                3
        );

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0).sectionTitle()).isEqualTo("Overview");
        assertThat(chunks.stream().map(DocumentChunk::sectionTitle).toList()).contains("Caching Strategy");
        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).chunk().sectionTitle()).isEqualTo("Caching Strategy");
    }

    @Test
    void keepsOverlapAndRealPageRangesAcrossPages() {
        SemanticChunker chunker = new SemanticChunker();
        String carryOver = repeatSentence("Carry over sentence keeps research context between chunk boundaries.", 8);
        List<DocumentChunk> chunks = chunker.chunk(
                "doc-2",
                "research-notes.txt",
                List.of(
                        new SemanticChunker.PageContent(
                                1,
                                "Execution Notes\n\n"
                                        + repeatSentence("Page one describes upload validation and document preprocessing stages.", 7)
                        ),
                        new SemanticChunker.PageContent(
                                2,
                                carryOver
                                        + "\n\n"
                                        + repeatSentence("Final section stores chunk metadata and manifest information for retrieval.", 10)
                        )
                )
        );

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0).pageStart()).isEqualTo(1);
        assertThat(chunks.get(0).pageEnd()).isEqualTo(2);
        assertThat(chunks.get(1).text()).contains("Carry over sentence keeps research context between chunk boundaries.");
        assertThat(chunks.get(1).pageStart()).isEqualTo(2);
    }

    @Test
    void readsLegacyChunkJsonWithoutNewMetadataFields() throws Exception {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();

        List<DocumentChunk> chunks = objectMapper.readValue(
                """
                [
                  {
                    "chunkId": "chunk-1",
                    "documentId": "doc-legacy",
                    "documentName": "legacy.txt",
                    "pageStart": 1,
                    "pageEnd": 1,
                    "text": "legacy chunk"
                  }
                ]
                """,
                new TypeReference<List<DocumentChunk>>() {
                }
        );

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkId()).isEqualTo("chunk-1");
        assertThat(chunks.get(0).sectionTitle()).isNull();
        assertThat(chunks.get(0).chunkOrder()).isNull();
    }

    private static String repeatSentence(String sentence, int count) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(sentence);
        }
        return builder.toString();
    }
}
