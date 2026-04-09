package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.RunEventEntity;
import com.xg.platform.contracts.message.RunEvent;

public class RunEventPersistenceConvertor {

    private final PersistenceJsonSupport jsonSupport;

    public RunEventPersistenceConvertor(ObjectMapper objectMapper) {
        this.jsonSupport = new PersistenceJsonSupport(objectMapper);
    }

    public RunEvent toRecord(RunEventEntity entity) {
        return new RunEvent(
                entity.getRunId(),
                entity.getThreadId(),
                entity.getEventType(),
                entity.getCreatedAt(),
                jsonSupport.readPayload(entity.getPayloadJson())
        );
    }

    public RunEventEntity toEntity(String userId, String threadId, RunEvent record) {
        RunEventEntity entity = new RunEventEntity();
        entity.setRunId(record.runId());
        entity.setUserId(userId);
        entity.setThreadId(threadId);
        entity.setEventType(record.eventType());
        entity.setPayloadJson(record.payload() == null ? null : jsonSupport.writeValue(record.payload(), "run event payload"));
        entity.setCreatedAt(record.timestamp());
        return entity;
    }
}
