package com.xg.platform.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.workspace.WorkspaceArea;
import com.xg.platform.workspace.WorkspaceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SkillPackageExecutor {

    private final SkillRegistry skillRegistry;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;
    private final List<String> pythonCommand;
    private final Duration timeout;
    private final Map<String, RunningSkillProcess> runningProcesses = new ConcurrentHashMap<>();

    public SkillPackageExecutor(SkillRegistry skillRegistry,
                                WorkspaceManager workspaceManager,
                                ObjectMapper objectMapper,
                                List<String> pythonCommand,
                                Duration timeout) {
        this.skillRegistry = skillRegistry;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
        this.pythonCommand = List.copyOf(pythonCommand);
        this.timeout = timeout;
    }

    public ToolExecutionResult runCommand(ToolExecutionRequest request, Map<String, String> envOverrides) {
        String skillId = request.arguments().path("skillId").asText("").trim();
        String commandId = request.arguments().path("command").asText("").trim();
        boolean background = request.arguments().path("background").asBoolean(false);
        String stdin = request.arguments().path("stdin").isMissingNode()
                ? ""
                : request.arguments().path("stdin").asText("");
        List<String> args = readStringArray(request.arguments().path("args"));
        if (skillId.isBlank()) {
            throw new IllegalArgumentException("run_skill_command requires skillId");
        }
        if (commandId.isBlank()) {
            throw new IllegalArgumentException("run_skill_command requires command");
        }
        SkillDefinition skill = skillRegistry.requireEnabledSkill(request.userId(), skillId, request.skillRuntimeSnapshot());
        SkillPackageCommand command = skill.packageCommands().stream()
                .filter(candidate -> candidate.commandId().equalsIgnoreCase(commandId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill command: " + commandId));
        Path skillRoot = skill.sourcePath().getParent();
        Path scriptPath = skillRoot.resolve(command.relativePath()).normalize();
        ensureInsideSkillRoot(skillRoot, scriptPath);
        List<String> fullCommand = buildCommand(command.runner(), scriptPath, args);
        if (background) {
            return startBackgroundProcess(request, skill, command, skillRoot, fullCommand, stdin, envOverrides);
        }
        return runForegroundProcess(request, skill, command, skillRoot, fullCommand, stdin, envOverrides);
    }

    public ToolExecutionResult processStatus(ToolExecutionRequest request) {
        String handleId = request.arguments().path("handleId").asText("").trim();
        if (handleId.isBlank()) {
            throw new IllegalArgumentException("skill_process_status requires handleId");
        }
        RunningSkillProcess process = runningProcesses.get(handleId);
        if (process == null) {
            throw new IllegalArgumentException("Unknown skill process handle: " + handleId);
        }
        return new ToolExecutionResult(request.tool().name(), buildProcessState(process, "status"), false, "ok");
    }

    public ToolExecutionResult stopProcess(ToolExecutionRequest request) {
        String handleId = request.arguments().path("handleId").asText("").trim();
        if (handleId.isBlank()) {
            throw new IllegalArgumentException("stop_skill_process requires handleId");
        }
        RunningSkillProcess process = runningProcesses.get(handleId);
        if (process == null) {
            throw new IllegalArgumentException("Unknown skill process handle: " + handleId);
        }
        if (process.process().isAlive()) {
            process.process().destroy();
            try {
                if (!process.process().waitFor(5, TimeUnit.SECONDS)) {
                    process.process().destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while stopping skill process: " + handleId, exception);
            }
        }
        return new ToolExecutionResult(request.tool().name(), buildProcessState(process, "stopped"), false, "ok");
    }

    private ToolExecutionResult startBackgroundProcess(ToolExecutionRequest request,
                                                       SkillDefinition skill,
                                                       SkillPackageCommand command,
                                                       Path skillRoot,
                                                       List<String> fullCommand,
                                                       String stdin,
                                                       Map<String, String> envOverrides) {
        String handleId = UUID.randomUUID().toString();
        String relativeLogPath = request.runId() + "/skill-packages/" + skill.skillId() + "/" + handleId + "/" + command.commandId() + ".log";
        Path logPath = workspaceManager.resolvePath(request.userId(), request.threadId(), WorkspaceArea.WORKSPACE, relativeLogPath);
        try {
            Files.createDirectories(logPath.getParent());
            ProcessBuilder processBuilder = new ProcessBuilder(fullCommand);
            processBuilder.directory(skillRoot.toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(logPath.toFile());
            mergeEnvironment(processBuilder.environment(), envOverrides);
            applyUtf8Environment(processBuilder.environment());
            Process process = processBuilder.start();
            if (!stdin.isBlank()) {
                try (OutputStream outputStream = process.getOutputStream()) {
                    outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }
            RunningSkillProcess running = new RunningSkillProcess(
                    handleId,
                    request.userId(),
                    request.threadId(),
                    skill.skillId(),
                    command.commandId(),
                    fullCommand,
                    logPath,
                    Instant.now(),
                    process
            );
            runningProcesses.put(handleId, running);
            return new ToolExecutionResult("run_skill_command", buildProcessState(running, "started"), false, "ok");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start background skill command: " + command.commandId(), exception);
        }
    }

    private ToolExecutionResult runForegroundProcess(ToolExecutionRequest request,
                                                     SkillDefinition skill,
                                                     SkillPackageCommand command,
                                                     Path skillRoot,
                                                     List<String> fullCommand,
                                                     String stdin,
                                                     Map<String, String> envOverrides) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(fullCommand);
            processBuilder.directory(skillRoot.toFile());
            processBuilder.redirectErrorStream(true);
            mergeEnvironment(processBuilder.environment(), envOverrides);
            applyUtf8Environment(processBuilder.environment());
            Process process = processBuilder.start();
            StreamCollector collector = collect(process.getInputStream(), skill.skillId() + "-" + command.commandId());
            try (OutputStream outputStream = process.getOutputStream()) {
                if (!stdin.isBlank()) {
                    outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
                }
            }
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            collector.await();
            var result = objectMapper.createObjectNode();
            result.put("skillId", skill.skillId());
            result.put("command", command.commandId());
            result.put("runner", command.runner().configValue());
            result.put("background", false);
            result.put("timedOut", !finished);
            result.put("exitCode", finished ? process.exitValue() : -1);
            result.put("stdout", collector.output());
            return new ToolExecutionResult("run_skill_command", result, !finished || process.exitValue() != 0, finished ? "ok" : "timeout");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute skill command: " + command.commandId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Skill command interrupted: " + command.commandId(), exception);
        }
    }

    private JsonNode buildProcessState(RunningSkillProcess process, String status) {
        var result = objectMapper.createObjectNode();
        boolean running = process.process().isAlive();
        result.put("handleId", process.handleId());
        result.put("skillId", process.skillId());
        result.put("command", process.commandId());
        result.put("status", status);
        result.put("running", running);
        result.put("pid", process.process().pid());
        result.put("startedAt", process.startedAt().toString());
        result.put("logPath", process.logPath().toString());
        if (!running) {
            result.put("exitCode", process.process().exitValue());
        }
        result.put("logTail", readTail(process.logPath(), 4000));
        return result;
    }

    private String readTail(Path path, int maxChars) {
        try {
            if (path == null || !Files.exists(path)) {
                return "";
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() <= maxChars) {
                return content;
            }
            return content.substring(content.length() - maxChars);
        } catch (IOException exception) {
            return "";
        }
    }

    private void ensureInsideSkillRoot(Path skillRoot, Path candidate) {
        if (skillRoot == null || candidate == null) {
            throw new IllegalArgumentException("Invalid skill command path");
        }
        Path normalizedRoot = skillRoot.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Skill command path escapes skill root");
        }
    }

    private List<String> buildCommand(SkillCommandRunner runner, Path scriptPath, List<String> args) {
        List<String> command = new ArrayList<>();
        switch (runner) {
            case PYTHON -> command.addAll(pythonCommand);
            case POWERSHELL -> {
                command.add("powershell");
                command.add("-NoProfile");
                command.add("-ExecutionPolicy");
                command.add("Bypass");
                command.add("-File");
            }
            case BASH -> command.add("bash");
            case CMD -> {
                command.add("cmd");
                command.add("/c");
            }
        }
        command.add(scriptPath.toString());
        if (args != null && !args.isEmpty()) {
            command.addAll(args);
        }
        return List.copyOf(command);
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isValueNode()) {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private void mergeEnvironment(Map<String, String> environment, Map<String, String> envOverrides) {
        if (envOverrides == null || envOverrides.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : envOverrides.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                environment.remove(key);
            } else {
                environment.put(key, value);
            }
        }
    }

    private void applyUtf8Environment(Map<String, String> environment) {
        environment.put("PYTHONIOENCODING", "utf-8");
        environment.put("PYTHONUTF8", "1");
        if (isWindows()) {
            putIfMissing(environment, "SystemRoot", System.getenv("SystemRoot"), System.getenv("windir"), "C:\\Windows");
            putIfMissing(environment, "windir", getenvIgnoreCase(environment, "SystemRoot"), System.getenv("windir"), "C:\\Windows");
            putIfMissing(environment, "TEMP", System.getenv("TEMP"), System.getProperty("java.io.tmpdir"));
            putIfMissing(environment, "TMP", System.getenv("TMP"), getenvIgnoreCase(environment, "TEMP"), System.getProperty("java.io.tmpdir"));
        }
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private void putIfMissing(Map<String, String> environment, String key, String... candidates) {
        if (hasText(getenvIgnoreCase(environment, key))) {
            return;
        }
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                environment.put(key, candidate);
                return;
            }
        }
    }

    private String getenvIgnoreCase(Map<String, String> environment, String key) {
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private StreamCollector collect(InputStream inputStream, String name) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try (inputStream; buffer) {
                inputStream.transferTo(buffer);
            } catch (IOException exception) {
                readFailure.set(exception);
            }
        }, "skill-package-output-" + name);
        thread.setDaemon(true);
        thread.start();
        return new StreamCollector(thread, buffer, readFailure, name);
    }

    private record RunningSkillProcess(
            String handleId,
            String userId,
            String threadId,
            String skillId,
            String commandId,
            List<String> command,
            Path logPath,
            Instant startedAt,
            Process process
    ) {
    }

    private record StreamCollector(Thread thread,
                                   ByteArrayOutputStream buffer,
                                   AtomicReference<IOException> readFailure,
                                   String name) {

        private void await() {
            try {
                thread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while reading skill command output: " + name, exception);
            }
            IOException exception = readFailure.get();
            if (exception != null) {
                throw new IllegalStateException("Failed to read skill command output: " + name, exception);
            }
        }

        private String output() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
