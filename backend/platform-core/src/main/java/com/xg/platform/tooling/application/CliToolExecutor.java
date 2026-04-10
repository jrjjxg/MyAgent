package com.xg.platform.tooling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

public class CliToolExecutor {

    private final ObjectMapper objectMapper;
    private final List<String> pythonCommand;
    private final Path scriptPath;
    private final Duration timeout;

    public CliToolExecutor(ObjectMapper objectMapper, List<String> pythonCommand, Path scriptPath, Duration timeout) {
        this.objectMapper = objectMapper;
        this.pythonCommand = List.copyOf(pythonCommand);
        this.scriptPath = scriptPath.toAbsolutePath().normalize();
        this.timeout = timeout;
    }

    public <T> T execute(String toolName, Object request, Class<T> responseType) {
        String output = executeRaw(toolName, request);
        try {
            return objectMapper.readValue(output, responseType);
        } catch (IOException exception) {
            throw new IllegalStateException("Tool returned invalid JSON: " + toolName, exception);
        }
    }

    public JsonNode executeJson(String toolName, Object request) {
        return executeJson(toolName, request, Map.of());
    }

    public JsonNode executeJson(String toolName, Object request, Map<String, String> envOverrides) {
        String output = executeRaw(toolName, request, envOverrides);
        try {
            return objectMapper.readTree(output);
        } catch (IOException exception) {
            throw new IllegalStateException("Tool returned invalid JSON: " + toolName, exception);
        }
    }

    private String executeRaw(String toolName, Object request) {
        return executeRaw(toolName, request, Map.of());
    }

    private String executeRaw(String toolName, Object request, Map<String, String> envOverrides) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(toolName));
            Map<String, String> environment = processBuilder.environment();
            ensureWindowsBootstrapEnvironment(environment);
            mergeEnvironment(environment, envOverrides);
            environment.put("PYTHONIOENCODING", "utf-8");
            environment.put("PYTHONUTF8", "1");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            StreamCollector collector = collect(process.getInputStream(), toolName);
            try (OutputStream outputStream = process.getOutputStream()) {
                objectMapper.writeValue(outputStream, request);
            }
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                collector.await();
                throw new IllegalStateException("Tool timed out: " + toolName + formatOutput(collector.output()));
            }
            collector.await();
            String output = collector.output();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Tool failed: " + toolName + " -> " + output.strip());
            }
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute tool: " + toolName, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tool execution interrupted: " + toolName, exception);
        }
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
                continue;
            }
            environment.put(key, value);
        }
    }

    private void ensureWindowsBootstrapEnvironment(Map<String, String> environment) {
        if (!isWindows()) {
            return;
        }

        putIfMissing(environment, "SystemRoot",
                System.getenv("SystemRoot"),
                System.getenv("windir"),
                "C:\\Windows");
        putIfMissing(environment, "windir",
                getenvIgnoreCase(environment, "SystemRoot"),
                System.getenv("windir"),
                "C:\\Windows");

        String systemRoot = getenvIgnoreCase(environment, "SystemRoot");
        putIfMissing(environment, "ComSpec",
                System.getenv("ComSpec"),
                systemRoot == null ? null : Path.of(systemRoot, "System32", "cmd.exe").toString());
        putIfMissing(environment, "TEMP",
                System.getenv("TEMP"),
                System.getProperty("java.io.tmpdir"));
        putIfMissing(environment, "TMP",
                System.getenv("TMP"),
                getenvIgnoreCase(environment, "TEMP"),
                System.getProperty("java.io.tmpdir"));
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

    private List<String> buildCommand(String toolName) {
        List<String> command = new java.util.ArrayList<>(pythonCommand);
        command.add(scriptPath.toString());
        command.add(toolName);
        return command;
    }

    private StreamCollector collect(InputStream inputStream, String toolName) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try (inputStream; buffer) {
                inputStream.transferTo(buffer);
            } catch (IOException exception) {
                readFailure.set(exception);
            }
        }, "cli-tool-output-" + toolName);
        thread.setDaemon(true);
        thread.start();
        return new StreamCollector(thread, buffer, readFailure, toolName);
    }

    private String formatOutput(String output) {
        String normalized = output == null ? "" : output.strip();
        return normalized.isEmpty() ? "" : " -> " + normalized;
    }

    private record StreamCollector(Thread thread,
                                   ByteArrayOutputStream buffer,
                                   AtomicReference<IOException> readFailure,
                                   String toolName) {

        private void await() {
            try {
                thread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while reading tool output: " + toolName, exception);
            }
            IOException exception = readFailure.get();
            if (exception != null) {
                throw new IllegalStateException("Failed to read tool output: " + toolName, exception);
            }
        }

        private String output() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
