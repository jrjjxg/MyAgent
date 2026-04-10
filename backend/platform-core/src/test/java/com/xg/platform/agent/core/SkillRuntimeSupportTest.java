package com.xg.platform.agent.core;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.xg.platform.skill.domain.SkillAvailabilityStatus;
import com.xg.platform.skill.domain.SkillDefinition;
import com.xg.platform.skill.domain.SkillExecutionMode;
import com.xg.platform.skill.domain.SkillInvocation;
import com.xg.platform.tooling.domain.ToolDescriptor;
import com.xg.platform.tooling.domain.ToolGroup;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import com.xg.platform.skill.runtime.SkillRuntimeSupport;

class SkillRuntimeSupportTest {

    @Test
    void prioritizeToolsForSkillsMovesPreferredToolsToFrontWithoutHidingFallbackTools() {
        ToolDescriptor weather = tool("weather");
        ToolDescriptor webSearch = tool("web_search");
        ToolDescriptor webFetch = tool("web_fetch");
        ToolDescriptor inspectDocument = tool("inspect_document");

        List<ToolDescriptor> prioritized = SkillRuntimeSupport.prioritizeToolsForSkills(
                List.of(webFetch, inspectDocument, weather, webSearch),
                List.of(weatherSkill())
        );

        assertThat(prioritized).extracting(ToolDescriptor::name).containsExactly(
                "weather",
                "web_search",
                "web_fetch",
                "inspect_document"
        );
    }

    private ToolDescriptor tool(String name) {
        return new ToolDescriptor(name, name, JsonMapper.builder().build().createObjectNode(), ToolGroup.SEARCH, "builtin");
    }

    private SkillDefinition weatherSkill() {
        return new SkillDefinition(
                "weather",
                "public:weather",
                "Weather workflow",
                "Use weather first.",
                "",
                "",
                List.of(),
                List.of("weather"),
                List.of("weather", "web_search", "web_fetch"),
                List.of("weather", "web_search", "web_fetch"),
                List.of(),
                List.of(),
                List.of(),
                false,
                true,
                "general-agent",
                SkillInvocation.AUTO,
                SkillExecutionMode.INLINE,
                Path.of("skills/public/weather/SKILL.md"),
                "",
                true,
                "public",
                SkillAvailabilityStatus.READY,
                ""
        );
    }
}
