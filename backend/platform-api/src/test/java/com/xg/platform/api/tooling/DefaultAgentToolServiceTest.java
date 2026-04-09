package com.xg.platform.api.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentToolServiceTest {

    @Test
    void listsResearchAndWorkspaceTools() {
        DefaultAgentToolService toolService = new DefaultAgentToolService(
                null,
                null,
                null,
                new ObjectMapper(),
                false
        );

        assertThat(toolService.listAvailableTools("user-1"))
                .extracting(tool -> tool.name())
                .contains(
                        "research_reflect",
                        "write_workspace_note",
                        "load_skill",
                        "load_skill_resource",
                        "list_workspace_documents",
                        "inspect_document",
                        "list_document_sections",
                        "search_document",
                        "read_document"
                );
    }
}
