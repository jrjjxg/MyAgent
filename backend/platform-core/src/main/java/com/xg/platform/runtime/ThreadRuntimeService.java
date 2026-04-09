package com.xg.platform.runtime;

import com.xg.platform.contracts.thread.ThreadRecord;

import java.util.List;

public class ThreadRuntimeService {

    private final ThreadRepository threadRepository;

    public ThreadRuntimeService(ThreadRepository threadRepository) {
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
