package com.xg.platform.runtime;

import com.xg.platform.contracts.thread.ThreadRecord;

import java.util.List;

public interface ThreadRepository {

    ThreadRecord createThread(String userId, String workspaceId, String title);

    List<ThreadRecord> listThreads(String userId);

    List<ThreadRecord> listThreads(String userId, String workspaceId);

    ThreadRecord getThread(String userId, String threadId);

    ThreadRecord touchThread(String userId, String threadId);

    void deleteThread(String userId, String threadId);
}
