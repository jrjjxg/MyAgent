package com.xg.platform.api.persistence.mybatisplus.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.persistence.mybatisplus.convertor.ResearchDraftPersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.ResearchDraftEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.ResearchDraftMapper;
import com.xg.platform.contracts.message.ResearchDraftRecord;
import com.xg.platform.runtime.ResearchDraftRepository;

import java.util.Optional;

public class MybatisResearchDraftRepository implements ResearchDraftRepository {

    private final ResearchDraftMapper researchDraftMapper;
    private final ResearchDraftPersistenceConvertor convertor;

    public MybatisResearchDraftRepository(ResearchDraftMapper researchDraftMapper, ObjectMapper objectMapper) {
        this.researchDraftMapper = researchDraftMapper;
        this.convertor = new ResearchDraftPersistenceConvertor(objectMapper);
    }

    @Override
    public Optional<ResearchDraftRecord> findActiveDraft(String userId, String threadId) {
        ResearchDraftEntity entity = researchDraftMapper.findActiveDraft(userId, threadId);
        return Optional.ofNullable(entity).map(convertor::toRecord);
    }

    @Override
    public ResearchDraftRecord save(String userId, ResearchDraftRecord draftRecord) {
        researchDraftMapper.upsert(convertor.toEntity(userId, draftRecord));
        return draftRecord;
    }

    @Override
    public void clear(String userId, String threadId) {
        researchDraftMapper.clearActiveDrafts(userId, threadId);
    }

    @Override
    public void deleteByThread(String userId, String threadId) {
        researchDraftMapper.delete(
                Wrappers.<ResearchDraftEntity>lambdaQuery()
                        .eq(ResearchDraftEntity::getUserId, userId)
                        .eq(ResearchDraftEntity::getThreadId, threadId)
        );
    }
}
