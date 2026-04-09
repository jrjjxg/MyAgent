package com.xg.platform.api.persistence.mybatisplus.convertor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.ResearchTaskSnapshotEntity;
import com.xg.platform.contracts.research.ReportCitation;
import com.xg.platform.contracts.research.ResearchFindingRecord;
import com.xg.platform.contracts.research.ResearchIterationRecord;
import com.xg.platform.contracts.research.ResearchReportSection;
import com.xg.platform.contracts.research.ResearchSourceRecord;
import com.xg.platform.contracts.research.ResearchTaskSnapshotRecord;

import java.util.List;

public class ResearchTaskSnapshotPersistenceConvertor {

    private static final TypeReference<SnapshotPayload> SNAPSHOT_PAYLOAD = new TypeReference<>() {
    };

    private final PersistenceJsonSupport jsonSupport;

    public ResearchTaskSnapshotPersistenceConvertor(ObjectMapper objectMapper) {
        this.jsonSupport = new PersistenceJsonSupport(objectMapper);
    }

    public ResearchTaskSnapshotRecord toRecord(ResearchTaskSnapshotEntity entity) {
        SnapshotPayload payload = jsonSupport.readValue(
                entity.getPayloadJson(),
                SNAPSHOT_PAYLOAD,
                new SnapshotPayload(List.of(), List.of(), List.of(), List.of(), List.of()),
                "research task snapshot payload"
        );
        return new ResearchTaskSnapshotRecord(
                entity.getTaskId(),
                entity.getThreadId(),
                entity.getPhase(),
                entity.getIterationNo(),
                payload.plan(),
                payload.iterations(),
                payload.findings(),
                payload.sources(),
                payload.citations(),
                entity.getSummary(),
                Boolean.TRUE.equals(entity.getConverged()),
                entity.getUpdatedAt()
        );
    }

    public ResearchTaskSnapshotEntity toEntity(String userId, ResearchTaskSnapshotRecord record) {
        ResearchTaskSnapshotEntity entity = new ResearchTaskSnapshotEntity();
        entity.setTaskId(record.taskId());
        entity.setUserId(userId);
        entity.setThreadId(record.threadId());
        entity.setPhase(record.phase());
        entity.setIterationNo(record.iterationNo());
        entity.setSummary(record.summary());
        entity.setConverged(record.converged());
        entity.setPayloadJson(jsonSupport.writeValue(
                new SnapshotPayload(
                        record.plan(),
                        record.iterations(),
                        record.findings(),
                        record.sources(),
                        record.citations()
                ),
                "research task snapshot payload"
        ));
        entity.setUpdatedAt(record.updatedAt());
        return entity;
    }

    private record SnapshotPayload(
            List<ResearchReportSection> plan,
            List<ResearchIterationRecord> iterations,
            List<ResearchFindingRecord> findings,
            List<ResearchSourceRecord> sources,
            List<ReportCitation> citations
    ) {
    }
}
