package com.xg.platform.api.persistence.mybatisplus.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.persistence.mybatisplus.convertor.MemoryExtractionJobPersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.MemoryExtractionJobEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.MemoryExtractionJobMapper;
import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;
import com.xg.platform.contracts.memory.MemoryExtractionJobStatus;
import com.xg.platform.memory.port.LongTermMemoryJobRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class MybatisLongTermMemoryJobRepository implements LongTermMemoryJobRepository {

    private final MemoryExtractionJobMapper memoryExtractionJobMapper;

    public MybatisLongTermMemoryJobRepository(MemoryExtractionJobMapper memoryExtractionJobMapper) {
        this.memoryExtractionJobMapper = memoryExtractionJobMapper;
    }

    @Override
    public Optional<MemoryExtractionJobRecord> createQueuedIfAbsent(String userId,
                                                                    String threadId,
                                                                    String messageId,
                                                                    String extractorVersion,
                                                                    int eligibleTurnCount) {
        Instant now = Instant.now();
        MemoryExtractionJobEntity entity = new MemoryExtractionJobEntity();
        entity.setJobId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setThreadId(threadId);
        entity.setMessageId(messageId);
        entity.setExtractorVersion(extractorVersion);
        entity.setStatus(MemoryExtractionJobStatus.QUEUED.name());
        entity.setAttemptCount(0);
        entity.setLastError(null);
        entity.setEligibleTurnCount(Math.max(1, eligibleTurnCount));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setStartedAt(null);
        entity.setCompletedAt(null);
        return Optional.ofNullable(memoryExtractionJobMapper.insertQueuedIfAbsent(entity))
                .map(MemoryExtractionJobPersistenceConvertor::toRecord);
    }

    @Override
    public Optional<MemoryExtractionJobRecord> findById(String jobId) {
        MemoryExtractionJobEntity entity = memoryExtractionJobMapper.selectOne(
                Wrappers.<MemoryExtractionJobEntity>lambdaQuery()
                        .eq(MemoryExtractionJobEntity::getJobId, jobId)
        );
        return Optional.ofNullable(entity).map(MemoryExtractionJobPersistenceConvertor::toRecord);
    }

    @Override
    public Optional<MemoryExtractionJobRecord> findLatestSucceeded(String userId, String threadId, String extractorVersion) {
        return Optional.ofNullable(memoryExtractionJobMapper.findLatestSucceeded(userId, threadId, extractorVersion))
                .map(MemoryExtractionJobPersistenceConvertor::toRecord);
    }

    @Override
    public boolean hasPendingJob(String userId, String threadId, String extractorVersion) {
        return memoryExtractionJobMapper.countPendingJobs(userId, threadId, extractorVersion) > 0;
    }

    @Override
    public Optional<MemoryExtractionJobRecord> markRunning(String jobId) {
        Instant now = Instant.now();
        int updated = memoryExtractionJobMapper.markRunning(
                jobId,
                MemoryExtractionJobStatus.RUNNING.name(),
                now,
                MemoryExtractionJobStatus.QUEUED.name()
        );
        return updated == 0 ? Optional.empty() : findById(jobId);
    }

    @Override
    public MemoryExtractionJobRecord markSucceeded(String jobId) {
        Instant now = Instant.now();
        memoryExtractionJobMapper.markSucceeded(jobId, MemoryExtractionJobStatus.SUCCEEDED.name(), now);
        return require(jobId);
    }

    @Override
    public MemoryExtractionJobRecord markFailure(String jobId, String lastError, boolean terminal) {
        Instant now = Instant.now();
        memoryExtractionJobMapper.markFailure(
                jobId,
                terminal ? MemoryExtractionJobStatus.FAILED.name() : MemoryExtractionJobStatus.QUEUED.name(),
                lastError,
                now,
                terminal ? now : null
        );
        return require(jobId);
    }

    @Override
    public void deleteByThread(String userId, String threadId) {
        memoryExtractionJobMapper.delete(
                Wrappers.<MemoryExtractionJobEntity>lambdaQuery()
                        .eq(MemoryExtractionJobEntity::getUserId, userId)
                        .eq(MemoryExtractionJobEntity::getThreadId, threadId)
        );
    }

    private MemoryExtractionJobRecord require(String jobId) {
        return findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Memory extraction job not found: " + jobId));
    }
}
