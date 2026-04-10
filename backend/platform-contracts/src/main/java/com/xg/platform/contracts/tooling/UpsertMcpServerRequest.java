package com.xg.platform.contracts.tooling;

import java.util.List;
import java.util.Map;

public record UpsertMcpServerRequest(
        String type,
        Boolean enabled,
        List<String> toolGroups,
        String url,
        String command,
        List<String> args,
        Map<String, String> env
) {
}
