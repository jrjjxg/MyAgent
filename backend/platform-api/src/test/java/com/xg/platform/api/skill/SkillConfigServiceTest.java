package com.xg.platform.api.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.skill.UpdateSkillConfigRequest;
import com.xg.platform.tools.McpServerRegistry;
import com.xg.platform.tools.SkillConfigStore;
import com.xg.platform.tools.SkillRegistry;
import com.xg.platform.tools.SkillUserConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SkillConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void mergesUserSecretsWithServerEnvWithoutReturningPlaintext() throws Exception {
        InMemorySkillConfigStore store = new InMemorySkillConfigStore();
        SkillRegistry registry = new SkillRegistry(tempDir, null, List.of(), new McpServerRegistry(writeExtensionsConfig(), new ObjectMapper()), store);
        writeSkill("baidu-search");
        store.save("user-1", "baidu-search", true, Map.of("BAIDU_API_KEY", "user-secret"));

        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("BAIDU_REGION", "bj");
        SkillConfigService service = new SkillConfigService(registry, store, environment);

        var response = service.listStatus("user-1");
        assertThat(response.secretStorageAvailable()).isTrue();
        assertThat(response.skills()).hasSize(1);
        var status = response.skills().get(0);
        assertThat(status.configuredEnvKeys()).containsExactly("BAIDU_API_KEY", "BAIDU_REGION");
        assertThat(status.missingEnvs()).isEmpty();
        assertThat(status.ready()).isTrue();
        assertThat(status.toString()).doesNotContain("user-secret");
        assertThat(service.resolveRuntimeEnv("user-1", List.of("baidu-search")))
                .containsEntry("BAIDU_API_KEY", "user-secret")
                .doesNotContainKey("BAIDU_REGION");
    }

    @Test
    void updatesOnlyDeclaredEnvKeysAndAllowsClearingPrimaryEnv() throws Exception {
        InMemorySkillConfigStore store = new InMemorySkillConfigStore();
        SkillRegistry registry = new SkillRegistry(tempDir, null, List.of(), new McpServerRegistry(writeExtensionsConfig(), new ObjectMapper()), store);
        writeSkill("baidu-search");
        SkillConfigService service = new SkillConfigService(registry, store, new MockEnvironment());

        service.updateSkillConfig("user-1", "baidu-search", new UpdateSkillConfigRequest(
                true,
                "first-secret",
                Map.of("BAIDU_REGION", "cn")
        ));
        service.updateSkillConfig("user-1", "baidu-search", new UpdateSkillConfigRequest(
                false,
                "",
                Map.of()
        ));

        SkillUserConfig config = store.find("user-1", "baidu-search").orElseThrow();
        assertThat(config.enabled()).isFalse();
        assertThat(config.env()).doesNotContainKey("BAIDU_API_KEY");
        assertThat(config.env()).containsEntry("BAIDU_REGION", "cn");
    }

    private void writeSkill(String skillId) throws Exception {
        Path skillDir = tempDir.resolve(skillId);
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: %s
                description: Search Baidu.
                primaryEnv: BAIDU_API_KEY
                requiredEnvs:
                  - BAIDU_API_KEY
                  - BAIDU_REGION
                ---
                # Skill
                """.formatted(skillId));
    }

    private Path writeExtensionsConfig() throws Exception {
        Path configPath = tempDir.resolve("extensions.json");
        Files.writeString(configPath, """
                {
                  "mcpServers": {}
                }
                """);
        return configPath;
    }

    private static final class InMemorySkillConfigStore implements SkillConfigStore {
        private final Map<String, SkillUserConfig> configs = new LinkedHashMap<>();

        @Override
        public Optional<SkillUserConfig> find(String userId, String skillId) {
            return Optional.ofNullable(configs.get(key(userId, skillId)));
        }

        @Override
        public List<SkillUserConfig> list(String userId) {
            return configs.values().stream().filter(config -> config.userId().equals(userId)).toList();
        }

        @Override
        public SkillUserConfig save(String userId, String skillId, boolean enabled, Map<String, String> env) {
            SkillUserConfig config = new SkillUserConfig(userId, skillId, enabled, env, Instant.now(), Instant.now());
            configs.put(key(userId, skillId), config);
            return config;
        }

        @Override
        public boolean secretStorageAvailable() {
            return true;
        }

        private String key(String userId, String skillId) {
            return userId + ":" + skillId;
        }
    }
}
