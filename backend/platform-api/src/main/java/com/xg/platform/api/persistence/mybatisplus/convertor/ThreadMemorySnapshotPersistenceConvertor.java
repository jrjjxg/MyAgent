package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.ThreadMemorySnapshotEntity;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.contracts.message.MessageRecord;

import java.util.List;

public final class ThreadMemorySnapshotPersistenceConvertor {

    private final ObjectMapper objectMapper;

    public ThreadMemorySnapshotPersistenceConvertor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ThreadMemorySnapshotRecord toRecord(ThreadMemorySnapshotEntity entity) {
        return new ThreadMemorySnapshotRecord(
                entity.getThreadId(),
                entity.getUserId(),
                entity.getSummary(),
                entity.getLastCompactedMessageId(),
                readPendingMessages(entity.getPendingHistoricalMessagesJson()),
                entity.getRecentEndMessageId(),
                entity.getRecentWindowSize() == null ? 20 : entity.getRecentWindowSize(),
                entity.getActiveDraftId(),
                entity.getActiveTaskId(),
                entity.getTaskStage(),
                readActiveSkillIds(entity.getActiveSkillIdsJson()),
                entity.getUpdatedAt()
        );
    }

    public ThreadMemorySnapshotEntity toEntity(String userId, ThreadMemorySnapshotRecord record) {
        ThreadMemorySnapshotEntity entity = new ThreadMemorySnapshotEntity();
        entity.setThreadId(record.threadId());
        entity.setUserId(userId);
        entity.setSummary(record.summary());
        entity.setLastCompactedMessageId(record.lastCompactedMessageId());
        entity.setPendingHistoricalMessagesJson(writePendingMessages(record.pendingHistoricalMessages()));
        entity.setRecentEndMessageId(record.recentEndMessageId());
        entity.setRecentWindowSize(record.recentWindowSize());
        entity.setActiveDraftId(record.activeDraftId());
        entity.setActiveTaskId(record.activeTaskId());
        entity.setTaskStage(record.taskStage());
        entity.setActiveSkillIdsJson(writeActiveSkillIds(record.activeSkillIds()));
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }

    private List<MessageRecord> readPendingMessages(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    serialized,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, MessageRecord.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize pending thread memory messages", exception);
        }
    }

    private String writePendingMessages(List<MessageRecord> pendingHistoricalMessages) {
        try {
            return objectMapper.writeValueAsString(pendingHistoricalMessages == null ? List.of() : pendingHistoricalMessages);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize pending thread memory messages", exception);
        }
    }

    private List<String> readActiveSkillIds(String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    serialized,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize thread active skill ids", exception);
        }
    }

    private String writeActiveSkillIds(List<String> activeSkillIds) {
        try {
            return objectMapper.writeValueAsString(activeSkillIds == null ? List.of() : activeSkillIds);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize thread active skill ids", exception);
        }
    }
}
