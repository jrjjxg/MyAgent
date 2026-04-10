package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.LongTermMemoryEntity;
import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryStatus;
import com.xg.platform.contracts.memory.LongTermMemoryType;

public final class LongTermMemoryPersistenceConvertor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private LongTermMemoryPersistenceConvertor() {
    }

    public static LongTermMemoryRecord toRecord(LongTermMemoryEntity entity) {
        return new LongTermMemoryRecord(
                entity.getMemoryId(),
                entity.getUserId(),
                memoryTypeOrDefault(entity.getMemoryType()),
                blankToNull(entity.getCanonicalKey()),
                entity.getTitle(),
                entity.getContent(),
                readValueJson(entity.getValueJson()),
                entity.getSourceThreadId(),
                entity.getSourceMessageId(),
                entity.getSourceTaskId(),
                LongTermMemoryStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static LongTermMemoryEntity toEntity(LongTermMemoryRecord record) {
        LongTermMemoryEntity entity = new LongTermMemoryEntity();
        entity.setMemoryId(record.memoryId());
        entity.setUserId(record.userId());
        entity.setMemoryType((record.memoryType() == null ? LongTermMemoryType.SEMANTIC : record.memoryType()).name());
        entity.setCanonicalKey(record.canonicalKey());
        entity.setTitle(record.title());
        entity.setContent(record.content());
        entity.setValueJson(writeValueJson(record.valueJson()));
        entity.setSourceThreadId(record.sourceThreadId());
        entity.setSourceMessageId(record.sourceMessageId());
        entity.setSourceTaskId(record.sourceTaskId());
        entity.setStatus(record.status().name());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }

    private static LongTermMemoryType memoryTypeOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return LongTermMemoryType.SEMANTIC;
        }
        return LongTermMemoryType.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static JsonNode readValueJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read long-term memory valueJson", exception);
        }
    }

    private static String writeValueJson(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to write long-term memory valueJson", exception);
        }
    }
}
