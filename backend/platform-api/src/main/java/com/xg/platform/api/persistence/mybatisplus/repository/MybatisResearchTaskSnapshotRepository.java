package com.xg.platform.api.persistence.mybatisplus.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.convertor.ResearchTaskSnapshotPersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.ResearchTaskSnapshotEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.ResearchTaskSnapshotMapper;
import com.xg.platform.contracts.research.ResearchTaskSnapshotRecord;
import com.xg.platform.research.port.ResearchTaskSnapshotRepository;

import java.util.Optional;

public class MybatisResearchTaskSnapshotRepository implements ResearchTaskSnapshotRepository {

    private final ResearchTaskSnapshotMapper researchTaskSnapshotMapper;
    private final ResearchTaskSnapshotPersistenceConvertor convertor;

    public MybatisResearchTaskSnapshotRepository(ResearchTaskSnapshotMapper researchTaskSnapshotMapper,
                                                 ObjectMapper objectMapper) {
        this.researchTaskSnapshotMapper = researchTaskSnapshotMapper;
        this.convertor = new ResearchTaskSnapshotPersistenceConvertor(objectMapper);
    }

    @Override
    public Optional<ResearchTaskSnapshotRecord> findByTask(String userId, String threadId, String taskId) {
        ResearchTaskSnapshotEntity entity = researchTaskSnapshotMapper.findByTask(userId, threadId, taskId);
        return Optional.ofNullable(entity).map(convertor::toRecord);
    }

    @Override
    public ResearchTaskSnapshotRecord save(String userId, ResearchTaskSnapshotRecord record) {
        researchTaskSnapshotMapper.upsert(convertor.toEntity(userId, record));
        return record;
    }

    @Override
    public void deleteByTask(String userId, String taskId) {
        researchTaskSnapshotMapper.delete(
                Wrappers.<ResearchTaskSnapshotEntity>lambdaQuery()
                        .eq(ResearchTaskSnapshotEntity::getUserId, userId)
                        .eq(ResearchTaskSnapshotEntity::getTaskId, taskId)
        );
    }

    @Override
    public void deleteByThread(String userId, String threadId) {
        researchTaskSnapshotMapper.delete(
                Wrappers.<ResearchTaskSnapshotEntity>lambdaQuery()
                        .eq(ResearchTaskSnapshotEntity::getUserId, userId)
                        .eq(ResearchTaskSnapshotEntity::getThreadId, threadId)
        );
    }
}
