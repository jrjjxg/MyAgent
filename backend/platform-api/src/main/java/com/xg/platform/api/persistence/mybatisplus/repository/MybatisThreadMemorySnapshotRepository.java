package com.xg.platform.api.persistence.mybatisplus.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.convertor.ThreadMemorySnapshotPersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.ThreadMemorySnapshotEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.ThreadMemorySnapshotMapper;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import java.util.Optional;

public class MybatisThreadMemorySnapshotRepository implements ThreadMemorySnapshotRepository {

    private final ThreadMemorySnapshotMapper threadMemorySnapshotMapper;
    private final ThreadMemorySnapshotPersistenceConvertor convertor;

    public MybatisThreadMemorySnapshotRepository(ThreadMemorySnapshotMapper threadMemorySnapshotMapper,
                                                 ObjectMapper objectMapper) {
        this.threadMemorySnapshotMapper = threadMemorySnapshotMapper;
        this.convertor = new ThreadMemorySnapshotPersistenceConvertor(objectMapper);
    }

    @Override
    public Optional<ThreadMemorySnapshotRecord> findByThread(String userId, String threadId) {
        ThreadMemorySnapshotEntity entity = threadMemorySnapshotMapper.findByThread(userId, threadId);
        return Optional.ofNullable(entity).map(convertor::toRecord);
    }

    @Override
    public ThreadMemorySnapshotRecord save(String userId, ThreadMemorySnapshotRecord record) {
        threadMemorySnapshotMapper.upsert(convertor.toEntity(userId, record));
        return record;
    }

    @Override
    public void deleteByThread(String userId, String threadId) {
        threadMemorySnapshotMapper.delete(
                Wrappers.<ThreadMemorySnapshotEntity>lambdaQuery()
                        .eq(ThreadMemorySnapshotEntity::getUserId, userId)
                        .eq(ThreadMemorySnapshotEntity::getThreadId, threadId)
        );
    }
}
