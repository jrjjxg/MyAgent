package com.xg.platform.workspace.application;

import com.xg.platform.contracts.workspace.ThreadRecord;

import java.util.List;
import com.xg.platform.workspace.port.ThreadRepository;

public class ThreadService {

    private final ThreadRepository threadRepository;

    public ThreadService(ThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    public ThreadRecord createThread(String userId, String workspaceId, String title) {
        return threadRepository.createThread(userId, workspaceId, title);
    }

    public List<ThreadRecord> listThreads(String userId) {
        return threadRepository.listThreads(userId);
    }

    public List<ThreadRecord> listThreads(String userId, String workspaceId) {
        return threadRepository.listThreads(userId, workspaceId);
    }

    public ThreadRecord getThread(String userId, String threadId) {
        return threadRepository.getThread(userId, threadId);
    }

    public ThreadRecord touchThread(String userId, String threadId) {
        return threadRepository.touchThread(userId, threadId);
    }
}
