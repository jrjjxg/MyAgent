package com.xg.platform.api.persistence.mybatisplus.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.persistence.mybatisplus.entity.RunEventEntity;
import com.xg.platform.api.persistence.mybatisplus.convertor.RunEventPersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.mapper.RunEventMapper;
import com.xg.platform.contracts.message.RunEvent;
import com.xg.platform.contracts.task.TaskRecord;
import com.xg.platform.runtime.RunEventRepository;

import java.util.List;

public class MybatisRunEventRepository implements RunEventRepository {

    private final RunEventMapper runEventMapper;
    private final RunEventPersistenceConvertor convertor;

    public MybatisRunEventRepository(RunEventMapper runEventMapper, ObjectMapper objectMapper) {
        this.runEventMapper = runEventMapper;
        this.convertor = new RunEventPersistenceConvertor(objectMapper);
    }

    @Override
    public void appendEvent(String userId, String threadId, RunEvent runEvent) {
        runEventMapper.insertEvent(convertor.toEntity(userId, threadId, runEvent));
    }

    @Override
    public List<RunEvent> listEvents(String userId, String threadId, String runId) {
        return runEventMapper.listByRunId(userId, threadId, runId)
                .stream()
                .map(convertor::toRecord)
                .toList();
    }

    @Override
    public List<RunEvent> listEvents(String userId, String threadId, int limit) {
        return runEventMapper.listLatest(userId, threadId, Math.max(1, limit))
                .stream()
                .map(convertor::toRecord)
                .toList();
    }

    @Override
    public List<RunEvent> listEvents(String userId, String threadId, List<TaskRecord> tasks, String taskId, int limit) {
        if (taskId != null && !taskId.isBlank()) {
            return listEvents(userId, threadId, taskId);
        }
        return listEvents(userId, threadId, limit);
    }

    @Override
    public void deleteByThread(String userId, String threadId) {
        runEventMapper.delete(
                Wrappers.<RunEventEntity>lambdaQuery()
                        .eq(RunEventEntity::getUserId, userId)
                        .eq(RunEventEntity::getThreadId, threadId)
        );
    }
}
