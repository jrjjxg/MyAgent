package com.xg.platform.contracts.tooling;

import java.util.List;
import java.util.Map;

public record McpServerDescriptor(
        String name,
        String type,
        boolean enabled,
        List<String> toolGroups,
        String url,
        String command,
        List<String> args,
        Map<String, String> env,
        String source
) {
}
