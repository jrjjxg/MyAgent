package com.xg.platform.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.skill.SkillDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.skill.domain.SkillAvailabilityStatus;
import com.xg.platform.skill.domain.SkillDefinition;
import com.xg.platform.skill.domain.SkillPackageCommand;
import com.xg.platform.skill.runtime.SkillCommandRunner;
import com.xg.platform.tooling.application.McpServerRegistry;

class SkillRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesOptionalSkillMetadataForDirectoryCards() throws Exception {
        Path skillDir = tempDir.resolve("weather");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: weather
                description: Forecast skill.
                summary: Use this for short weather lookups.
                homepage: https://example.com/weather
                primaryEnv: WEATHER_API_KEY
                requiredEnvs:
                  - WEATHER_API_KEY
                  - WEATHER_REGION
                triggers:
                  - weather
                  - forecast
                preferredTools:
                  - weather
                  - web_fetch
                allowedTools:
                  - weather
                  - web_search
                resources:
                  - references/checklist.md
                mcpServers:
                  - search
                invocation: auto
                execution: inline
                requiresDocuments: false
                requiresWeb: true
                agent: general-agent
                ---
                # Weather
                """);

        SkillRegistry registry = new SkillRegistry(tempDir, new McpServerRegistry(writeExtensionsConfig(), new ObjectMapper()));
        var descriptor = registry.listDescriptors("user-1").get(0);

        assertThat(descriptor.skillId()).isEqualTo("weather");
        assertThat(descriptor.summary()).isEqualTo("Use this for short weather lookups.");
        assertThat(descriptor.homepage()).isEqualTo("https://example.com/weather");
        assertThat(descriptor.primaryEnv()).isEqualTo("WEATHER_API_KEY");
        assertThat(descriptor.requiredEnvs()).containsExactly("WEATHER_API_KEY", "WEATHER_REGION");
        assertThat(descriptor.triggers()).containsExactly("weather", "forecast");
        assertThat(descriptor.preferredTools()).containsExactly("weather", "web_fetch");
        assertThat(descriptor.allowedTools()).containsExactly("weather", "web_search");
        assertThat(descriptor.resources()).containsExactly("references/checklist.md");
        assertThat(descriptor.mcpServers()).containsExactly("search");
        assertThat(descriptor.invocation()).isEqualTo("auto");
        assertThat(descriptor.execution()).isEqualTo("inline");
        assertThat(descriptor.requiresDocuments()).isFalse();
        assertThat(descriptor.requiresWeb()).isTrue();
        assertThat(descriptor.sourceKey()).isEqualTo("public:weather");
        assertThat(descriptor.status()).isEqualTo("missing_env");
        assertThat(descriptor.statusReason()).contains("WEATHER_API_KEY", "WEATHER_REGION");
    }

    @Test
    void resolvesSkillFromHighestPrioritySource() throws Exception {
        Path publicRoot = tempDir.resolve("public");
        Path customRoot = tempDir.resolve("custom");
        Path extraRoot = tempDir.resolve("extra");
        writeSkill(publicRoot.resolve("weather"), "Public weather");
        writeSkill(customRoot.resolve("weather"), "Custom weather");
        writeSkill(extraRoot.resolve("weather"), "Extra weather");

        SkillRegistry registry = new SkillRegistry(
                publicRoot,
                customRoot,
                java.util.List.of(extraRoot),
                new McpServerRegistry(writeExtensionsConfig(), new ObjectMapper())
        );

        SkillDefinition resolved = registry.snapshotForUser("user-1").requireEnabledSkill("weather");

        assertThat(resolved.description()).isEqualTo("Extra weather");
        assertThat(resolved.sourceKey()).isEqualTo("extra-1:weather");
    }

    @Test
    void discoversPackagedCommandsFromScriptsDirectory() throws Exception {
        Path skillDir = tempDir.resolve("stock-monitor");
        Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: stock-monitor
                description: Monitor watched stocks.
                agent: general-agent
                ---
                # Stock Monitor
                """);
        Files.writeString(skillDir.resolve("scripts").resolve("monitor.py"), "print('monitor')");
        Files.writeString(skillDir.resolve("scripts").resolve("monitor_daemon.py"), "print('daemon')");

        SkillRegistry registry = new SkillRegistry(tempDir, new McpServerRegistry(writeExtensionsConfig(), new ObjectMapper()));
        SkillDefinition skill = registry.snapshotForUser("user-1").requireEnabledSkill("stock-monitor");

        assertThat(skill.packageCommands())
                .extracting(SkillPackageCommand::commandId)
                .containsExactly("monitor", "monitor_daemon");
        assertThat(skill.packageCommands())
                .extracting(SkillPackageCommand::runner)
                .containsOnly(SkillCommandRunner.PYTHON);
        assertThat(skill.packageCommands())
                .filteredOn(SkillPackageCommand::backgroundSuggested)
                .extracting(SkillPackageCommand::commandId)
                .containsExactly("monitor_daemon");
    }

    @Test
    void listDescriptorsIncludesPublicAndCustomSkills() throws Exception {
        Path publicRoot = tempDir.resolve("public");
        Path customRoot = tempDir.resolve("custom");
        writeSkill(publicRoot.resolve("weather"), "Public weather");
        writeSkill(customRoot.resolve("stock-monitor"), "Custom stock monitor", "stock-monitor");

        SkillRegistry registry = new SkillRegistry(
                publicRoot,
                customRoot,
                List.of(),
                new McpServerRegistry(writeExtensionsConfig(), new ObjectMapper())
        );

        assertThat(registry.listDescriptors("user-1"))
                .extracting(SkillDescriptor::skillId)
                .containsExactly("stock-monitor", "weather");
    }

    @Test
    void discoveredCatalogKeepsBodyDeferredUntilExplicitLoad() throws Exception {
        Path skillDir = tempDir.resolve("weather");
        Path referencesDir = skillDir.resolve("references");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: weather
                description: Forecast skill.
                resources:
                  - references/guide.md
                agent: general-agent
                ---
                # Weather

                Full body content.
                """);
        Files.createDirectories(referencesDir);
        Files.writeString(referencesDir.resolve("guide.md"), "Forecast guide");

        SkillRegistry registry = new SkillRegistry(tempDir, new McpServerRegistry(writeExtensionsConfig(), new ObjectMapper()));

        SkillDefinition discovered = registry.listDiscoveredSkills().get(0);
        SkillDefinition loaded = registry.loadSkillContent("user-1", "weather");

        assertThat(discovered.body()).isEmpty();
        assertThat(loaded.body()).contains("Full body content.");
        assertThat(loaded.availabilityStatus()).isEqualTo(SkillAvailabilityStatus.READY);
        assertThat(registry.loadSkillResource("user-1", "weather", "references/guide.md", null).text()).isEqualTo("Forecast guide");
    }

    @Test
    void loadSkillResourceRejectsUndeclaredOrEscapedPaths() throws Exception {
        Path skillDir = tempDir.resolve("weather");
        Path referencesDir = skillDir.resolve("references");
        Files.createDirectories(referencesDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: weather
                description: Forecast skill.
                resources:
                  - references/guide.md
                agent: general-agent
                ---
                # Weather
                """);
        Files.writeString(referencesDir.resolve("guide.md"), "Forecast guide");
        SkillRegistry registry = new SkillRegistry(tempDir, new McpServerRegistry(writeExtensionsConfig(), new ObjectMapper()));

        assertThatThrownBy(() -> registry.loadSkillResource("user-1", "weather", "../secrets.txt", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource not declared");
    }

    @Test
    void discoversMigratedCustomSkillsFromRepositoryLayout() {
        Path repoRoot = locateRepositoryRoot();
        Path skillsRoot = repoRoot.resolve("skills");

        SkillRegistry registry = new SkillRegistry(
                skillsRoot.resolve("public"),
                skillsRoot.resolve("custom"),
                List.of(),
                new McpServerRegistry(repoRoot.resolve("extensions.json"), new ObjectMapper())
        );

        List<SkillDescriptor> descriptors = registry.listDescriptors("user-1");

        assertThat(descriptors)
                .extracting(SkillDescriptor::skillId)
                .contains("baidu-search", "firecrawl", "humanizer", "markdown", "summarize");

        SkillDefinition baidu = registry.requireSkill("user-1", "baidu-search");
        assertThat(baidu.primaryEnv()).isEqualTo("BAIDU_API_KEY");
        assertThat(baidu.resources()).containsExactly("references/apikey-fetch.md");
        assertThat(baidu.packageCommands())
                .extracting(SkillPackageCommand::commandId)
                .containsExactly("search");

        SkillDefinition firecrawl = registry.requireSkill("user-1", "firecrawl");
        assertThat(firecrawl.primaryEnv()).isEqualTo("FIRECRAWL_API_KEY");
        assertThat(firecrawl.resources()).containsExactly("references/api.md");
        assertThat(firecrawl.packageCommands())
                .extracting(SkillPackageCommand::commandId)
                .containsExactly("crawl", "scrape", "search");

        SkillDefinition humanizer = registry.loadSkillContent("user-1", "humanizer");
        assertThat(humanizer.resources()).containsExactly("README.md");
        assertThat(humanizer.body()).contains("You are a writing editor that identifies and removes signs of AI-generated text");

        SkillDefinition markdown = registry.loadSkillContent("user-1", "markdown");
        assertThat(markdown.body()).contains("## Whitespace Traps");

        SkillDefinition summarize = registry.requireSkill("user-1", "summarize");
        assertThat(summarize.packageCommands()).isEmpty();
        assertThat(summarize.description()).contains("summarize CLI");
    }

    private Path locateRepositoryRoot() {
        Path candidate = Path.of(System.getProperty("maven.multiModuleProjectDirectory", "."))
                .toAbsolutePath()
                .normalize();
        Path resolved = findRepositoryRoot(candidate);
        if (resolved != null) {
            return resolved;
        }
        resolved = findRepositoryRoot(Path.of("").toAbsolutePath().normalize());
        if (resolved != null) {
            return resolved;
        }
        throw new IllegalStateException("Could not locate repository root containing skills/custom");
    }

    private Path findRepositoryRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isDirectory(current.resolve("skills").resolve("custom"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private void writeSkill(Path skillDir, String description) throws Exception {
        writeSkill(skillDir, description, "weather");
    }

    private void writeSkill(Path skillDir, String description, String skillId) throws Exception {
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: %s
                description: %s
                agent: general-agent
                ---
                # Weather
                """.formatted(skillId, description));
    }

    private Path writeExtensionsConfig() throws Exception {
        Path configPath = tempDir.resolve("extensions.json");
        Files.writeString(configPath, """
                {
                  "mcpServers": {
                    "search": {
                      "enabled": true,
                      "toolGroups": ["search"]
                    }
                  }
                }
                """);
        return configPath;
    }
}
