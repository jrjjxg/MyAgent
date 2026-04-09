package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.xg.platform.api.persistence.mybatisplus.entity.MemoryExtractionJobEntity;
import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;
import com.xg.platform.contracts.memory.MemoryExtractionJobStatus;

public final class MemoryExtractionJobPersistenceConvertor {

    private MemoryExtractionJobPersistenceConvertor() {
    }

    public static MemoryExtractionJobRecord toRecord(MemoryExtractionJobEntity entity) {
        return new MemoryExtractionJobRecord(
                entity.getJobId(),
                entity.getUserId(),
                entity.getThreadId(),
                entity.getMessageId(),
                entity.getExtractorVersion(),
                MemoryExtractionJobStatus.valueOf(entity.getStatus()),
                entity.getAttemptCount() == null ? 0 : entity.getAttemptCount(),
                entity.getLastError(),
                entity.getEligibleTurnCount() == null ? 1 : entity.getEligibleTurnCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    public static MemoryExtractionJobEntity toEntity(MemoryExtractionJobRecord record) {
        MemoryExtractionJobEntity entity = new MemoryExtractionJobEntity();
        entity.setJobId(record.jobId());
        entity.setUserId(record.userId());
        entity.setThreadId(record.threadId());
        entity.setMessageId(record.messageId());
        entity.setExtractorVersion(record.extractorVersion());
        entity.setStatus(record.status().name());
        entity.setAttemptCount(record.attemptCount());
        entity.setLastError(record.lastError());
        entity.setEligibleTurnCount(record.eligibleTurnCount() == null ? 1 : record.eligibleTurnCount());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        entity.setStartedAt(record.startedAt());
        entity.setCompletedAt(record.completedAt());
        return entity;
    }
}
