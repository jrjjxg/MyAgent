package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PersistenceJsonSupport {

    private final ObjectMapper objectMapper;

    public PersistenceJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    public String writeValue(Object value, String description) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write " + description, exception);
        }
    }

    public <T> T readValue(String json, TypeReference<T> typeReference, T defaultValue, String description) {
        try {
            if (json == null || json.isBlank()) {
                return defaultValue;
            }
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read " + description, exception);
        }
    }

    public Object readPayload(String json) {
        try {
            if (json == null || json.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json);
            return node.isNull() ? null : node;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read run event payload", exception);
        }
    }
}
