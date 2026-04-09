package com.xg.platform.api.persistence.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.TaskEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface TaskMapper extends BaseMapper<TaskEntity> {

    TaskEntity insertQueuedIngestIfAbsent(@Param("task") TaskEntity task);

    TaskEntity selectByUserAndTaskId(@Param("userId") String userId, @Param("taskId") String taskId);

    List<TaskEntity> listByWorkspace(@Param("userId") String userId,
                                     @Param("workspaceId") String workspaceId,
                                     @Param("kind") String kind);

    TaskEntity findIngestTaskByDocument(@Param("userId") String userId,
                                        @Param("workspaceId") String workspaceId,
                                        @Param("documentId") String documentId);

    int claimQueuedOrStaleRunningTask(@Param("userId") String userId,
                                      @Param("taskId") String taskId,
                                      @Param("summary") String summary,
                                      @Param("stage") String stage,
                                      @Param("progress") Integer progress,
                                      @Param("updatedAt") Instant updatedAt,
                                      @Param("staleRunningBefore") Instant staleRunningBefore);
}
