package com.xg.platform.tooling.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.Serializable;

public record ToolDescriptor(
        String name,
        String description,
        JsonNode inputSchema,
        ToolGroup group,
        String source
) implements Serializable {
}
