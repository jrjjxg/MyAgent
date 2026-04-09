package com.xg.platform.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CliToolExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void readsLargeStdoutWithoutDeadlocking() throws Exception {
        List<String> pythonCommand = detectPythonCommand();
        Assumptions.assumeFalse(pythonCommand.isEmpty(), "Python launcher is not available in this environment");

        Path script = tempDir.resolve("emit_json.py");
        Files.writeString(script, """
                import json
                import sys

                def main():
                    if len(sys.argv) != 2:
                        raise SystemExit("missing tool name")
                    json.load(sys.stdin)
                    if sys.argv[1] != "emit":
                        raise SystemExit("bad tool")
                    json.dump({"value": "x" * 200000}, sys.stdout)

                if __name__ == "__main__":
                    main()
                """);

        CliToolExecutor executor = new CliToolExecutor(
                new ObjectMapper(),
                pythonCommand,
                script,
                Duration.ofSeconds(5)
        );

        LargeResponse response = executor.execute("emit", Map.of("ok", true), LargeResponse.class);

        assertThat(response.value()).hasSize(200000);
    }

    private record LargeResponse(String value) {
    }

    private List<String> detectPythonCommand() {
        List<List<String>> candidates = List.of(
                List.of("py", "-3"),
                List.of("python"),
                List.of("python3")
        );
        for (List<String> candidate : candidates) {
            try {
                List<String> command = new java.util.ArrayList<>(candidate);
                command.add("--version");
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                if (process.waitFor() == 0) {
                    return candidate;
                }
            } catch (IOException | InterruptedException ignored) {
                if (ignored instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return List.of();
    }
}
