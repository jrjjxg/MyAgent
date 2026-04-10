package com.xg.platform.api.persistence.mybatisplus.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.persistence.mybatisplus.convertor.LongTermMemoryPersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.LongTermMemoryEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.LongTermMemoryMapper;
import com.xg.platform.contracts.memory.CreateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryStatus;
import com.xg.platform.contracts.memory.LongTermMemoryType;
import com.xg.platform.contracts.memory.UpdateLongTermMemoryRequest;
import com.xg.platform.memory.application.LongTermMemoryKeyRegistry;
import com.xg.platform.memory.port.LongTermMemoryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MybatisLongTermMemoryRepository implements LongTermMemoryRepository {

    private final LongTermMemoryMapper longTermMemoryMapper;

    public MybatisLongTermMemoryRepository(LongTermMemoryMapper longTermMemoryMapper) {
        this.longTermMemoryMapper = longTermMemoryMapper;
    }

    @Override
    public List<LongTermMemoryRecord> listActive(String userId) {
        return longTermMemoryMapper.selectList(
                        Wrappers.<LongTermMemoryEntity>lambdaQuery()
                                .eq(LongTermMemoryEntity::getUserId, userId)
                                .eq(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.ACTIVE.name())
                                .orderByDesc(LongTermMemoryEntity::getUpdatedAt))
                .stream()
                .map(LongTermMemoryPersistenceConvertor::toRecord)
                .toList();
    }

    @Override
    public Optional<LongTermMemoryRecord> findById(String userId, String memoryId) {
        LongTermMemoryEntity entity = longTermMemoryMapper.selectOne(
                Wrappers.<LongTermMemoryEntity>lambdaQuery()
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getMemoryId, memoryId)
        );
        return Optional.ofNullable(entity).map(LongTermMemoryPersistenceConvertor::toRecord);
    }

    @Override
    public Optional<LongTermMemoryRecord> findActiveByCanonicalKey(String userId,
                                                                   LongTermMemoryType memoryType,
                                                                   String canonicalKey) {
        String normalizedKey = LongTermMemoryKeyRegistry.normalizeToken(canonicalKey);
        if (normalizedKey == null) {
            return Optional.empty();
        }
        LongTermMemoryEntity entity = longTermMemoryMapper.selectOne(
                Wrappers.<LongTermMemoryEntity>lambdaQuery()
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.ACTIVE.name())
                        .eq(LongTermMemoryEntity::getMemoryType, normalizeType(memoryType).name())
                        .eq(LongTermMemoryEntity::getCanonicalKey, normalizedKey)
                        .orderByDesc(LongTermMemoryEntity::getUpdatedAt)
                        .last("limit 1")
        );
        return Optional.ofNullable(entity).map(LongTermMemoryPersistenceConvertor::toRecord);
    }

    @Override
    public LongTermMemoryRecord create(String userId, CreateLongTermMemoryRequest request) {
        LongTermMemoryKeyRegistry.NormalizedMemory normalizedMemory = LongTermMemoryKeyRegistry.normalizeForWrite(
                request.memoryType(),
                request.canonicalKey(),
                request.title(),
                request.sourceMessageId()
        );
        Optional<LongTermMemoryRecord> existing = normalizedMemory.memoryType() == LongTermMemoryType.EPISODIC
                ? findActiveEpisodicBySourceMessageId(userId, request.sourceMessageId())
                : findActiveByCanonicalKey(userId, normalizedMemory.memoryType(), normalizedMemory.canonicalKey());
        if (existing.isPresent()) {
            return update(userId, existing.orElseThrow().memoryId(), new UpdateLongTermMemoryRequest(
                    normalizedMemory.memoryType(),
                    normalizedMemory.canonicalKey(),
                    request.title(),
                    request.content(),
                    request.valueJson(),
                    request.sourceThreadId(),
                    request.sourceMessageId(),
                    request.sourceTaskId()
            ));
        }
        Instant now = Instant.now();
        LongTermMemoryRecord record = new LongTermMemoryRecord(
                UUID.randomUUID().toString(),
                userId,
                normalizedMemory.memoryType(),
                normalizedMemory.canonicalKey(),
                trim(request.title()),
                trim(request.content()),
                request.valueJson(),
                trim(request.sourceThreadId()),
                trim(request.sourceMessageId()),
                trim(request.sourceTaskId()),
                LongTermMemoryStatus.ACTIVE,
                now,
                now
        );
        longTermMemoryMapper.insert(LongTermMemoryPersistenceConvertor.toEntity(record));
        return record;
    }

    @Override
    public LongTermMemoryRecord update(String userId, String memoryId, UpdateLongTermMemoryRequest request) {
        LongTermMemoryRecord existing = findById(userId, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Long-term memory not found: " + memoryId));
        LongTermMemoryType targetType = request.memoryType() == null ? existing.memoryType() : request.memoryType();
        String targetTitle = trimOrDefault(request.title(), existing.title());
        String targetSourceMessageId = trimOrDefault(request.sourceMessageId(), existing.sourceMessageId());
        LongTermMemoryKeyRegistry.NormalizedMemory normalizedMemory = LongTermMemoryKeyRegistry.normalizeForWrite(
                targetType,
                request.canonicalKey() == null ? existing.canonicalKey() : request.canonicalKey(),
                targetTitle,
                targetSourceMessageId
        );
        if (normalizedMemory.memoryType() == LongTermMemoryType.EPISODIC) {
            assertUniqueActiveEpisodeSourceMessage(userId, memoryId, targetSourceMessageId);
        }
        assertUniqueActiveKey(userId, memoryId, normalizedMemory.memoryType(), normalizedMemory.canonicalKey());
        LongTermMemoryRecord updated = new LongTermMemoryRecord(
                existing.memoryId(),
                existing.userId(),
                normalizedMemory.memoryType(),
                normalizedMemory.canonicalKey(),
                targetTitle,
                trimOrDefault(request.content(), existing.content()),
                request.valueJson() == null ? existing.valueJson() : request.valueJson(),
                trimOrDefault(request.sourceThreadId(), existing.sourceThreadId()),
                targetSourceMessageId,
                trimOrDefault(request.sourceTaskId(), existing.sourceTaskId()),
                existing.status(),
                existing.createdAt(),
                Instant.now()
        );
        LongTermMemoryEntity entity = LongTermMemoryPersistenceConvertor.toEntity(updated);
        longTermMemoryMapper.updateById(entity);
        return updated;
    }

    @Override
    public void delete(String userId, String memoryId) {
        longTermMemoryMapper.update(
                null,
                Wrappers.<LongTermMemoryEntity>lambdaUpdate()
                        .set(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.DELETED.name())
                        .set(LongTermMemoryEntity::getUpdatedAt, Instant.now())
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getMemoryId, memoryId)
        );
    }

    @Override
    public int deleteBySourceThread(String userId, String sourceThreadId) {
        if (sourceThreadId == null || sourceThreadId.isBlank()) {
            return 0;
        }
        return longTermMemoryMapper.update(
                null,
                Wrappers.<LongTermMemoryEntity>lambdaUpdate()
                        .set(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.DELETED.name())
                        .set(LongTermMemoryEntity::getUpdatedAt, Instant.now())
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getSourceThreadId, sourceThreadId)
                        .eq(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.ACTIVE.name())
        );
    }

    private void assertUniqueActiveKey(String userId,
                                       String memoryId,
                                       LongTermMemoryType memoryType,
                                       String canonicalKey) {
        LongTermMemoryEntity duplicate = longTermMemoryMapper.selectOne(
                Wrappers.<LongTermMemoryEntity>lambdaQuery()
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.ACTIVE.name())
                        .eq(LongTermMemoryEntity::getMemoryType, memoryType.name())
                        .eq(LongTermMemoryEntity::getCanonicalKey, canonicalKey)
                        .ne(LongTermMemoryEntity::getMemoryId, memoryId)
                        .last("limit 1")
        );
        if (duplicate != null) {
            throw new IllegalArgumentException("Long-term memory key already exists: " + canonicalKey);
        }
    }

    private void assertUniqueActiveEpisodeSourceMessage(String userId, String memoryId, String sourceMessageId) {
        LongTermMemoryEntity duplicate = longTermMemoryMapper.selectOne(
                Wrappers.<LongTermMemoryEntity>lambdaQuery()
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.ACTIVE.name())
                        .eq(LongTermMemoryEntity::getMemoryType, LongTermMemoryType.EPISODIC.name())
                        .eq(LongTermMemoryEntity::getSourceMessageId, sourceMessageId)
                        .ne(LongTermMemoryEntity::getMemoryId, memoryId)
                        .last("limit 1")
        );
        if (duplicate != null) {
            throw new IllegalArgumentException("Episodic memory source already exists: " + sourceMessageId);
        }
    }

    private Optional<LongTermMemoryRecord> findActiveEpisodicBySourceMessageId(String userId, String sourceMessageId) {
        if (sourceMessageId == null || sourceMessageId.isBlank()) {
            return Optional.empty();
        }
        LongTermMemoryEntity entity = longTermMemoryMapper.selectOne(
                Wrappers.<LongTermMemoryEntity>lambdaQuery()
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.ACTIVE.name())
                        .eq(LongTermMemoryEntity::getMemoryType, LongTermMemoryType.EPISODIC.name())
                        .eq(LongTermMemoryEntity::getSourceMessageId, sourceMessageId.trim())
                        .orderByDesc(LongTermMemoryEntity::getUpdatedAt)
                        .last("limit 1")
        );
        return Optional.ofNullable(entity).map(LongTermMemoryPersistenceConvertor::toRecord);
    }

    private LongTermMemoryType normalizeType(LongTermMemoryType memoryType) {
        return memoryType == null ? LongTermMemoryType.SEMANTIC : memoryType;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
