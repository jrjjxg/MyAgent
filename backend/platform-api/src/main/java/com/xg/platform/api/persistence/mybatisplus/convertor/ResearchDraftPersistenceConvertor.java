package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.ResearchDraftEntity;
import com.xg.platform.contracts.research.ResearchDraftRecord;
import com.xg.platform.contracts.research.ResearchDraftStatus;
import com.xg.platform.contracts.research.ResearchPlanStep;

import java.util.List;

public class ResearchDraftPersistenceConvertor {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ResearchPlanStep>> PLAN_STEP_LIST = new TypeReference<>() {
    };

    private final PersistenceJsonSupport jsonSupport;

    public ResearchDraftPersistenceConvertor(ObjectMapper objectMapper) {
        this.jsonSupport = new PersistenceJsonSupport(objectMapper);
    }

    public ResearchDraftRecord toRecord(ResearchDraftEntity entity) {
        return new ResearchDraftRecord(
                entity.getDraftId(),
                entity.getThreadId(),
                ResearchDraftStatus.valueOf(entity.getStatus()),
                entity.getTitle(),
                entity.getBrief(),
                entity.getObjective(),
                entity.getScope(),
                entity.getOutputFormat(),
                jsonSupport.readValue(entity.getConstraintsJson(), STRING_LIST, List.of(), "draft constraints"),
                jsonSupport.readValue(entity.getQuestionsJson(), STRING_LIST, List.of(), "draft questions"),
                entity.getRevision(),
                entity.getPlanSummary(),
                jsonSupport.readValue(entity.getPlanStepsJson(), PLAN_STEP_LIST, List.of(), "draft plan steps"),
                Boolean.TRUE.equals(entity.getReady()),
                entity.getLastUserMessageId(),
                entity.getLastAssistantMessageId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public ResearchDraftEntity toEntity(String userId, ResearchDraftRecord record) {
        ResearchDraftEntity entity = new ResearchDraftEntity();
        entity.setDraftId(record.draftId());
        entity.setUserId(userId);
        entity.setThreadId(record.threadId());
        entity.setStatus(record.status().name());
        entity.setTitle(record.title());
        entity.setBrief(record.brief());
        entity.setObjective(record.objective());
        entity.setScope(record.scope());
        entity.setOutputFormat(record.outputFormat());
        entity.setConstraintsJson(jsonSupport.writeValue(record.constraints() == null ? List.of() : record.constraints(), "draft constraints"));
        entity.setQuestionsJson(jsonSupport.writeValue(record.questions() == null ? List.of() : record.questions(), "draft questions"));
        entity.setRevision(record.revision());
        entity.setPlanSummary(record.planSummary());
        entity.setPlanStepsJson(jsonSupport.writeValue(record.planSteps() == null ? List.of() : record.planSteps(), "draft plan steps"));
        entity.setReady(record.ready());
        entity.setLastUserMessageId(record.lastUserMessageId());
        entity.setLastAssistantMessageId(record.lastAssistantMessageId());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }
}
