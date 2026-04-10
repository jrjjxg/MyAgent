package com.xg.platform.shared.port;

import com.xg.platform.contracts.shared.event.RunEvent;
import com.xg.platform.contracts.shared.task.TaskRecord;

import java.util.List;

public interface RunEventRepository {

    void appendEvent(String userId, String threadId, RunEvent runEvent);

    List<RunEvent> listEvents(String userId, String threadId, String runId);

    List<RunEvent> listEvents(String userId, String threadId, int limit);

    List<RunEvent> listEvents(String userId, String threadId, List<TaskRecord> tasks, String taskId, int limit);

    void deleteByThread(String userId, String threadId);
}
