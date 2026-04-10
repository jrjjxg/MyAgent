package com.xg.platform.api.persistence.mybatisplus.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xg.platform.api.persistence.mybatisplus.convertor.ThreadPersistenceConvertor;
import com.xg.platform.api.persistence.mybatisplus.entity.ThreadEntity;
import com.xg.platform.api.persistence.mybatisplus.mapper.ThreadMapper;
import com.xg.platform.contracts.workspace.ThreadRecord;
import com.xg.platform.contracts.workspace.ThreadStatus;
import com.xg.platform.contracts.shared.validation.PlatformIds;
import com.xg.platform.workspace.port.ThreadRepository;
import com.xg.platform.workspace.port.WorkspaceRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public class MybatisThreadRepository implements ThreadRepository {

    private final ThreadMapper threadMapper;
    private final WorkspaceRepository workspaceRepository;

    public MybatisThreadRepository(ThreadMapper threadMapper, WorkspaceRepository workspaceRepository) {
        this.threadMapper = threadMapper;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public ThreadRecord createThread(String userId, String workspaceId, String title) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        workspaceRepository.getWorkspace(safeUserId, safeWorkspaceId);
        Instant now = Instant.now();
        ThreadRecord thread = new ThreadRecord(
                UUID.randomUUID().toString(),
                safeUserId,
                safeWorkspaceId,
                normalizeTitle(title),
                ThreadStatus.IDLE,
                now,
                now
        );
        threadMapper.insert(ThreadPersistenceConvertor.toEntity(thread));
        return thread;
    }

    @Override
    public List<ThreadRecord> listThreads(String userId) {
        return threadMapper.selectList(
                Wrappers.<ThreadEntity>lambdaQuery()
                                .eq(ThreadEntity::getUserId, PlatformIds.requireUserId(userId))
                                .orderByDesc(ThreadEntity::getUpdatedAt))
                .stream()
                .map(ThreadPersistenceConvertor::toRecord)
                .toList();
    }

    @Override
    public List<ThreadRecord> listThreads(String userId, String workspaceId) {
        String safeUserId = PlatformIds.requireUserId(userId);
        String safeWorkspaceId = PlatformIds.requireWorkspaceId(workspaceId);
        workspaceRepository.getWorkspace(safeUserId, safeWorkspaceId);
        return threadMapper.selectList(
                        Wrappers.<ThreadEntity>lambdaQuery()
                                .eq(ThreadEntity::getUserId, safeUserId)
                                .eq(ThreadEntity::getWorkspaceId, safeWorkspaceId)
                                .orderByDesc(ThreadEntity::getUpdatedAt))
                .stream()
                .map(ThreadPersistenceConvertor::toRecord)
                .toList();
    }

    @Override
    public ThreadRecord getThread(String userId, String threadId) {
        ThreadEntity entity = threadMapper.selectOne(
                Wrappers.<ThreadEntity>lambdaQuery()
                        .eq(ThreadEntity::getUserId, PlatformIds.requireUserId(userId))
                        .eq(ThreadEntity::getThreadId, PlatformIds.requireThreadId(threadId))
        );
        if (entity == null) {
            throw new NoSuchElementException("Thread not found: " + threadId);
        }
        return ThreadPersistenceConvertor.toRecord(entity);
    }

    @Override
    public ThreadRecord touchThread(String userId, String threadId) {
        ThreadRecord existing = getThread(userId, threadId);
        Instant now = Instant.now();
        threadMapper.update(
                null,
                Wrappers.<ThreadEntity>lambdaUpdate()
                        .set(ThreadEntity::getUpdatedAt, now)
                        .eq(ThreadEntity::getUserId, PlatformIds.requireUserId(userId))
                        .eq(ThreadEntity::getThreadId, PlatformIds.requireThreadId(threadId))
        );
        return new ThreadRecord(
                existing.threadId(),
                existing.userId(),
                existing.workspaceId(),
                existing.title(),
                existing.status(),
                existing.createdAt(),
                now
        );
    }

    @Override
    public void deleteThread(String userId, String threadId) {
        threadMapper.delete(
                Wrappers.<ThreadEntity>lambdaQuery()
                        .eq(ThreadEntity::getUserId, PlatformIds.requireUserId(userId))
                        .eq(ThreadEntity::getThreadId, PlatformIds.requireThreadId(threadId))
        );
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "New thread";
        }
        return title.trim();
    }
}
