package com.xg.platform.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.AgentToolService;
import com.xg.platform.api.tooling.DefaultAgentToolService;
import com.xg.platform.api.skill.SkillConfigService;
import com.xg.platform.agent.core.DocumentIngestService;
import com.xg.platform.document.application.ChunkIndexStore;
import com.xg.platform.document.application.ContextAssembler;
import com.xg.platform.document.application.DocumentStore;
import com.xg.platform.document.application.SemanticChunker;
import com.xg.platform.shared.port.RunEventRepository;
import com.xg.platform.shared.runtime.async.TaskDispatcher;
import com.xg.platform.shared.port.TaskRepository;
import com.xg.platform.workspace.application.ThreadService;
import com.xg.platform.tooling.application.BuiltinToolExecutor;
import com.xg.platform.tooling.application.BuiltinWeatherClient;
import com.xg.platform.tooling.application.BuiltinWebResearchClient;
import com.xg.platform.tooling.application.CliToolExecutor;
import com.xg.platform.tooling.application.McpServerRegistry;
import com.xg.platform.tooling.application.McpToolExecutor;
import com.xg.platform.skill.port.SkillConfigStore;
import com.xg.platform.skill.runtime.SkillPackageExecutor;
import com.xg.platform.skill.application.SkillRegistry;
import com.xg.platform.tooling.port.WebSearchSettingsResolver;
import com.xg.platform.tooling.application.WorkspaceDocumentToolSupport;
import com.xg.platform.workspace.application.ArtifactService;
import com.xg.platform.workspace.application.WorkspaceManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

@Configuration(proxyBeanMethods = false)
public class ToolingConfig {

    private static final Logger logger = Logger.getLogger(ToolingConfig.class.getName());

    @Bean
    ContextAssembler contextAssembler() {
        return new ContextAssembler();
    }

    @Bean
    SemanticChunker semanticChunker() {
        return new SemanticChunker();
    }

    @Bean
    McpServerRegistry mcpServerRegistry(PlatformProperties properties, ObjectMapper objectMapper) {
        return new McpServerRegistry(properties.getResolvedExtensionsConfigPath(), objectMapper.copy());
    }

    @Bean
    SkillRegistry skillRegistry(PlatformProperties properties,
                                McpServerRegistry mcpServerRegistry,
                                SkillConfigStore skillConfigStore) {
        Path skillsRoot = properties.getResolvedSkillsRoot();
        Path publicRoot = Files.isDirectory(skillsRoot.resolve("public"))
                ? skillsRoot.resolve("public")
                : skillsRoot;
        Path customRoot = properties.getSkills().getCustomRoot() == null
                ? skillsRoot.resolve("custom")
                : properties.resolvePath(properties.getSkills().getCustomRoot());
        List<Path> extraRoots = properties.getSkills().getExtraRoots().stream()
                .map(properties::resolvePath)
                .toList();
        return new SkillRegistry(publicRoot, customRoot, extraRoots, mcpServerRegistry, skillConfigStore);
    }

    @Bean
    SkillConfigService skillConfigService(SkillRegistry skillRegistry,
                                          SkillConfigStore skillConfigStore,
                                          Environment environment) {
        return new SkillConfigService(skillRegistry, skillConfigStore, environment);
    }

    @Bean
    CliToolExecutor cliToolExecutor(PlatformProperties properties, ObjectMapper objectMapper) {
        List<String> pythonCommand = resolvePythonCommand(properties.getTools().getPythonCommand());
        validateDocumentToolRuntime(pythonCommand);
        return new CliToolExecutor(
                objectMapper.copy(),
                pythonCommand,
                properties.resolvePath(properties.getTools().getDocumentScript()),
                Duration.ofSeconds(properties.getTools().getTimeoutSeconds())
        );
    }

    @Bean
    SkillPackageExecutor skillPackageExecutor(PlatformProperties properties,
                                              WorkspaceManager workspaceManager,
                                              SkillRegistry skillRegistry,
                                              ObjectMapper objectMapper) {
        List<String> pythonCommand = resolvePythonCommand(properties.getTools().getPythonCommand());
        return new SkillPackageExecutor(
                skillRegistry,
                workspaceManager,
                objectMapper.copy(),
                pythonCommand,
                Duration.ofSeconds(properties.getTools().getTimeoutSeconds())
        );
    }

    @Bean
    BuiltinWebResearchClient builtinWebResearchClient(PlatformProperties properties,
                                                      ObjectMapper objectMapper,
                                                      WebSearchSettingsResolver webSearchSettingsResolver) {
        return new BuiltinWebResearchClient(
                objectMapper.copy(),
                webSearchSettingsResolver,
                properties.getTools().getWeb().getUserAgent(),
                Duration.ofSeconds(properties.getTools().getWeb().getTimeoutSeconds()),
                properties.getTools().getWeb().getMaxResults(),
                properties.getDebug().isLogAgentFlow()
        );
    }

    @Bean
    BuiltinWeatherClient builtinWeatherClient(PlatformProperties properties, ObjectMapper objectMapper) {
        return new BuiltinWeatherClient(
                objectMapper.copy(),
                Duration.ofSeconds(properties.getTools().getWeb().getTimeoutSeconds())
        );
    }

    @Bean
    BuiltinToolExecutor builtinToolExecutor(WorkspaceManager workspaceManager,
                                            SkillRegistry skillRegistry,
                                            BuiltinWebResearchClient builtinWebResearchClient,
                                            BuiltinWeatherClient builtinWeatherClient,
                                            SkillPackageExecutor skillPackageExecutor,
                                            WorkspaceDocumentToolSupport workspaceDocumentToolSupport,
                                            ObjectMapper objectMapper) {
        return new BuiltinToolExecutor(
                workspaceManager,
                skillRegistry,
                builtinWebResearchClient,
                builtinWeatherClient,
                skillPackageExecutor,
                workspaceDocumentToolSupport,
                objectMapper.copy()
        );
    }

    @Bean
    WorkspaceDocumentToolSupport workspaceDocumentToolSupport(DocumentStore documentStore,
                                                              ArtifactService artifactService,
                                                              WorkspaceManager workspaceManager,
                                                              ContextAssembler contextAssembler,
                                                              ObjectMapper objectMapper) {
        return new WorkspaceDocumentToolSupport(
                documentStore,
                artifactService,
                workspaceManager,
                contextAssembler,
                objectMapper.copy()
        );
    }

    @Bean
    McpToolExecutor mcpToolExecutor() {
        return new McpToolExecutor();
    }

    @Bean
    AgentToolService agentToolService(CliToolExecutor cliToolExecutor,
                                      BuiltinToolExecutor builtinToolExecutor,
                                      McpToolExecutor mcpToolExecutor,
                                      ObjectMapper objectMapper,
                                      PlatformProperties properties,
                                      SkillConfigService skillConfigService) {
        return new DefaultAgentToolService(
                cliToolExecutor,
                builtinToolExecutor,
                mcpToolExecutor,
                objectMapper.copy(),
                properties.getDebug().isLogAgentFlow(),
                skillConfigService
        );
    }

    @Bean
    DocumentIngestService documentIngestService(DocumentStore documentStore,
                                                ChunkIndexStore chunkIndexStore,
                                                ArtifactService artifactService,
                                                WorkspaceManager workspaceManager,
                                                TaskRepository taskRepository,
                                                RunEventRepository runEventRepository,
                                                ThreadService threadRuntimeService,
                                                CliToolExecutor cliToolExecutor,
                                                ObjectMapper objectMapper,
                                                TaskDispatcher taskDispatcher,
                                                SemanticChunker semanticChunker,
                                                PlatformProperties properties) {
        return new DocumentIngestService(
                documentStore,
                chunkIndexStore,
                artifactService,
                workspaceManager,
                taskRepository,
                runEventRepository,
                threadRuntimeService,
                cliToolExecutor,
                objectMapper.copy(),
                taskDispatcher,
                semanticChunker,
                new DocumentIngestService.Settings(
                        properties.getIngest().getMaxAttempts(),
                        properties.getIngest().getStaleRunningMinutes()
                )
        );
    }

    private List<String> resolvePythonCommand(String configuredCommand) {
        List<List<String>> candidates = pythonCommandCandidates(configuredCommand);
        for (List<String> candidate : candidates) {
            if (isCommandAvailable(candidate)) {
                logger.info(() -> "Resolved python command for tools: " + String.join(" ", candidate));
                return List.copyOf(candidate);
            }
        }
        List<String> fallback = candidates.isEmpty() ? List.of("python") : candidates.get(0);
        logger.warning(() -> "No working python launcher detected; falling back to configured command: " + String.join(" ", fallback));
        return List.copyOf(fallback);
    }

    private List<List<String>> pythonCommandCandidates(String configuredCommand) {
        Set<String> seen = new LinkedHashSet<>();
        List<List<String>> candidates = new ArrayList<>();
        addPythonCandidate(candidates, seen, tokenizeCommand(configuredCommand));
        addPythonCandidate(candidates, seen, List.of("py", "-3"));
        addPythonCandidate(candidates, seen, List.of("python"));
        addPythonCandidate(candidates, seen, List.of("python3"));
        return List.copyOf(candidates);
    }

    private void addPythonCandidate(List<List<String>> candidates, Set<String> seen, List<String> candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        String key = String.join("\u0000", candidate).toLowerCase(Locale.ROOT);
        if (seen.add(key)) {
            candidates.add(List.copyOf(candidate));
        }
    }

    private List<String> tokenizeCommand(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        String[] tokens = command.trim().split("\\s+");
        List<String> values = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                values.add(token);
            }
        }
        return List.copyOf(values);
    }

    private boolean isCommandAvailable(List<String> command) {
        try {
            List<String> probe = new ArrayList<>(command);
            probe.add("--version");
            Process process = new ProcessBuilder(probe)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void validateDocumentToolRuntime(List<String> pythonCommand) {
        List<String> probe = new ArrayList<>(pythonCommand);
        probe.add("-c");
        probe.add("import fitz, docx");
        try {
            Process process = new ProcessBuilder(probe)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warning(() -> "Document tool runtime probe timed out; PDF/DOCX extraction may fail."
                        + " Verify python dependencies with: "
                        + String.join(" ", pythonCommand)
                        + " -m pip install PyMuPDF python-docx");
                return;
            }
            if (process.exitValue() != 0) {
                String message = new String(process.getInputStream().readAllBytes()).trim();
                logger.warning(() -> "Document tool runtime probe failed; PDF/DOCX extraction may fail."
                        + " command=" + String.join(" ", pythonCommand)
                        + " error=" + message
                        + " fix=\"" + String.join(" ", pythonCommand) + " -m pip install PyMuPDF python-docx\"");
            }
        } catch (IOException exception) {
            logger.warning(() -> "Document tool runtime probe failed to execute: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warning("Document tool runtime probe interrupted.");
        }
    }
}
