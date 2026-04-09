package com.xg.platform.api.persistence.mybatisplus.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.persistence.mybatisplus.convertor.WorkspacePersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.WorkspaceEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.WorkspaceMapper;
import com.xg.platform.contracts.validation.PlatformIds;
import com.xg.platform.contracts.workspace.WorkspaceRecord;
import com.xg.platform.contracts.workspace.WorkspaceStatus;
import com.xg.platform.runtime.WorkspaceRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public class MybatisWorkspaceRepository implements WorkspaceRepository {

    private final WorkspaceMapper workspaceMapper;

    public MybatisWorkspaceRepository(WorkspaceMapper workspaceMapper) {
        this.workspaceMapper = workspaceMapper;
    }

    @Override
    public WorkspaceRecord createWorkspace(String userId, String title) {
        String safeUserId = PlatformIds.requireUserId(userId);
        Instant now = Instant.now();
        WorkspaceRecord record = new WorkspaceRecord(
                UUID.randomUUID().toString(),
                safeUserId,
                normalizeTitle(title),
                WorkspaceStatus.ACTIVE,
                now,
                now
        );
        workspaceMapper.insert(WorkspacePersistenceConvertor.toEntity(record));
        return record;
    }

    @Override
    public List<WorkspaceRecord> listWorkspaces(String userId) {
        return workspaceMapper.selectList(
                        Wrappers.<WorkspaceEntity>lambdaQuery()
                                .eq(WorkspaceEntity::getUserId, PlatformIds.requireUserId(userId))
                                .orderByDesc(WorkspaceEntity::getUpdatedAt))
                .stream()
                .map(WorkspacePersistenceConvertor::toRecord)
                .toList();
    }

    @Override
    public WorkspaceRecord getWorkspace(String userId, String workspaceId) {
        WorkspaceEntity entity = workspaceMapper.selectOne(
                Wrappers.<WorkspaceEntity>lambdaQuery()
                        .eq(WorkspaceEntity::getUserId, PlatformIds.requireUserId(userId))
                        .eq(WorkspaceEntity::getWorkspaceId, PlatformIds.requireWorkspaceId(workspaceId))
        );
        if (entity == null) {
            throw new NoSuchElementException("Workspace not found: " + workspaceId);
        }
        return WorkspacePersistenceConvertor.toRecord(entity);
    }

    @Override
    public WorkspaceRecord touchWorkspace(String userId, String workspaceId) {
        WorkspaceRecord existing = getWorkspace(userId, workspaceId);
        Instant now = Instant.now();
        workspaceMapper.update(
                null,
                Wrappers.<WorkspaceEntity>lambdaUpdate()
                        .set(WorkspaceEntity::getUpdatedAt, now)
                        .eq(WorkspaceEntity::getUserId, PlatformIds.requireUserId(userId))
                        .eq(WorkspaceEntity::getWorkspaceId, PlatformIds.requireWorkspaceId(workspaceId))
        );
        return new WorkspaceRecord(
                existing.workspaceId(),
                existing.userId(),
                existing.title(),
                existing.status(),
                existing.createdAt(),
                now
        );
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "New workspace";
        }
        return title.trim();
    }
}
