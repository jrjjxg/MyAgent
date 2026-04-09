package com.xg.platform.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xg.platform.contracts.mcp.McpServerDescriptor;
import com.xg.platform.contracts.mcp.UpsertMcpServerRequest;
import com.xg.platform.contracts.validation.PlatformIds;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class McpServerRegistry {

    private final Path globalExtensionsConfigPath;
    private final ObjectMapper objectMapper;

    public McpServerRegistry(Path globalExtensionsConfigPath, ObjectMapper objectMapper) {
        this.globalExtensionsConfigPath = globalExtensionsConfigPath.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public boolean isSkillEnabled(String userId, String skillId) {
        JsonNode skills = readGlobalConfig().path("skills");
        JsonNode skill = skills.path(skillId);
        if (skill.isMissingNode()) {
            return true;
        }
        return skill.path("enabled").asBoolean(true);
    }

    public void setSkillEnabled(String userId, String skillId, boolean enabled) {
        throw new UnsupportedOperationException("User-managed skill flags are disabled");
    }

    public boolean hasSearchTools(String userId) {
        return !enabledSearchServers(userId).isEmpty();
    }

    public Set<String> enabledSearchServers(String userId) {
        Set<String> names = new HashSet<>();
        for (McpServerDescriptor descriptor : listServers(userId)) {
            if (!descriptor.enabled()) {
                continue;
            }
            if (descriptor.toolGroups() == null) {
                continue;
            }
            for (String group : descriptor.toolGroups()) {
                if ("search".equalsIgnoreCase(group)) {
                    names.add(descriptor.name());
                    break;
                }
            }
        }
        return names;
    }

    public List<McpServerDescriptor> listServers(String userId) {
        JsonNode mergedServers = readGlobalConfig().path("mcpServers");
        if (!mergedServers.isObject()) {
            return List.of();
        }
        List<McpServerDescriptor> descriptors = new ArrayList<>();
        mergedServers.fields().forEachRemaining(entry -> descriptors.add(toDescriptor(entry.getKey(), entry.getValue())));
        return descriptors.stream()
                .sorted(Comparator.comparing(McpServerDescriptor::name))
                .toList();
    }

    public McpServerDescriptor upsertServer(String userId, String name, UpsertMcpServerRequest request) {
        PlatformIds.requireIdentifier(name, "name");
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        throw new UnsupportedOperationException("User-managed MCP servers are disabled");
    }

    public void deleteServer(String userId, String name) {
        PlatformIds.requireIdentifier(name, "name");
        throw new UnsupportedOperationException("User-managed MCP servers are disabled");
    }

    private McpServerDescriptor toDescriptor(String name, JsonNode value) {
        List<String> toolGroups = new ArrayList<>();
        if (value.path("toolGroups").isArray()) {
            value.path("toolGroups").forEach(node -> toolGroups.add(node.asText()));
        }
        Map<String, String> env = new HashMap<>();
        if (value.path("env").isObject()) {
            value.path("env").fields().forEachRemaining(entry -> env.put(entry.getKey(), entry.getValue().asText()));
        }
        List<String> args = new ArrayList<>();
        if (value.path("args").isArray()) {
            value.path("args").forEach(node -> args.add(node.asText()));
        }
        return new McpServerDescriptor(
                name,
                value.path("type").asText("custom"),
                value.path("enabled").asBoolean(false),
                List.copyOf(toolGroups),
                textOrNull(value.path("url")),
                textOrNull(value.path("command")),
                List.copyOf(args),
                Map.copyOf(env),
                "system"
        );
    }

    private ObjectNode readGlobalConfig() {
        if (!Files.exists(globalExtensionsConfigPath)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(globalExtensionsConfigPath.toFile());
            return node != null && node.isObject() ? (ObjectNode) node : objectMapper.createObjectNode();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read global extensions config", exception);
        }
    }

    private String textOrNull(JsonNode node) {
        String value = node.asText("").trim();
        return value.isBlank() ? null : value;
    }
}
