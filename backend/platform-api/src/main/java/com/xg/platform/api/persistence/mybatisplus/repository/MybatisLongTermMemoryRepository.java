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
import com.xg.platform.runtime.LongTermMemoryRepository;

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
    public Optional<LongTermMemoryRecord> findActiveByTitle(String userId, String title) {
        LongTermMemoryEntity entity = longTermMemoryMapper.selectOne(
                Wrappers.<LongTermMemoryEntity>lambdaQuery()
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.ACTIVE.name())
                        .apply("lower(title) = lower({0})", title == null ? "" : title.trim())
                        .orderByDesc(LongTermMemoryEntity::getUpdatedAt)
                        .last("limit 1")
        );
        return Optional.ofNullable(entity).map(LongTermMemoryPersistenceConvertor::toRecord);
    }

    @Override
    public Optional<LongTermMemoryRecord> findActiveByCanonicalKey(String userId,
                                                                   LongTermMemoryType memoryType,
                                                                   String canonicalKey) {
        String normalizedKey = normalizeCanonicalKey(canonicalKey);
        if (normalizedKey == null) {
            return Optional.empty();
        }
        LongTermMemoryEntity entity = longTermMemoryMapper.selectOne(
                Wrappers.<LongTermMemoryEntity>lambdaQuery()
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getStatus, LongTermMemoryStatus.ACTIVE.name())
                        .eq(LongTermMemoryEntity::getMemoryType, memoryTypeOrDefault(memoryType).name())
                        .apply("lower(canonical_key) = lower({0})", normalizedKey)
                        .orderByDesc(LongTermMemoryEntity::getUpdatedAt)
                        .last("limit 1")
        );
        return Optional.ofNullable(entity).map(LongTermMemoryPersistenceConvertor::toRecord);
    }

    @Override
    public LongTermMemoryRecord create(String userId, CreateLongTermMemoryRequest request) {
        Instant now = Instant.now();
        LongTermMemoryRecord record = new LongTermMemoryRecord(
                UUID.randomUUID().toString(),
                userId,
                memoryTypeOrDefault(request.memoryType()),
                deriveCanonicalKey(request.memoryType(), request.canonicalKey(), request.title()),
                trim(request.title()),
                trim(request.content()),
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
        LongTermMemoryRecord updated = new LongTermMemoryRecord(
                existing.memoryId(),
                existing.userId(),
                request.memoryType() == null ? existing.memoryType() : request.memoryType(),
                deriveCanonicalKey(
                        request.memoryType() == null ? existing.memoryType() : request.memoryType(),
                        trimOrDefault(request.canonicalKey(), existing.canonicalKey()),
                        trimOrDefault(request.title(), existing.title())
                ),
                trimOrDefault(request.title(), existing.title()),
                trimOrDefault(request.content(), existing.content()),
                trimOrDefault(request.sourceThreadId(), existing.sourceThreadId()),
                trimOrDefault(request.sourceMessageId(), existing.sourceMessageId()),
                trimOrDefault(request.sourceTaskId(), existing.sourceTaskId()),
                existing.status(),
                existing.createdAt(),
                Instant.now()
        );
        longTermMemoryMapper.update(
                null,
                Wrappers.<LongTermMemoryEntity>lambdaUpdate()
                        .set(LongTermMemoryEntity::getMemoryType, updated.memoryType().name())
                        .set(LongTermMemoryEntity::getCanonicalKey, updated.canonicalKey())
                        .set(LongTermMemoryEntity::getTitle, updated.title())
                        .set(LongTermMemoryEntity::getContent, updated.content())
                        .set(LongTermMemoryEntity::getSourceThreadId, updated.sourceThreadId())
                        .set(LongTermMemoryEntity::getSourceMessageId, updated.sourceMessageId())
                        .set(LongTermMemoryEntity::getSourceTaskId, updated.sourceTaskId())
                        .set(LongTermMemoryEntity::getUpdatedAt, updated.updatedAt())
                        .eq(LongTermMemoryEntity::getUserId, userId)
                        .eq(LongTermMemoryEntity::getMemoryId, memoryId)
        );
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

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private LongTermMemoryType memoryTypeOrDefault(LongTermMemoryType type) {
        return type == null ? LongTermMemoryType.SEMANTIC : type;
    }

    private String deriveCanonicalKey(LongTermMemoryType type, String candidate, String title) {
        String normalized = normalizeCanonicalKey(candidate);
        if (normalized != null) {
            return normalized;
        }
        String prefix = switch (memoryTypeOrDefault(type)) {
            case PROFILE -> "profile";
            case SEMANTIC -> "semantic";
            case EPISODIC -> "episode";
        };
        String suffix = normalizeCanonicalKey(title);
        return suffix == null ? prefix : prefix + "." + suffix;
    }

    private String normalizeCanonicalKey(String value) {
        String trimmed = trim(value);
        if (trimmed == null || trimmed.isBlank()) {
            return null;
        }
        String normalized = trimmed.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");
        return normalized.isBlank() ? null : normalized;
    }
}
