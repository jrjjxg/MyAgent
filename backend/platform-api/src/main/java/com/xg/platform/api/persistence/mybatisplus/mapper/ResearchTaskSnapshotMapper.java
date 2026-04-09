package com.xg.platform.api.persistence.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.ResearchTaskSnapshotEntity;
import org.apache.ibatis.annotations.Param;

public interface ResearchTaskSnapshotMapper extends BaseMapper<ResearchTaskSnapshotEntity> {

    ResearchTaskSnapshotEntity findByTask(@Param("userId") String userId,
                                          @Param("threadId") String threadId,
                                          @Param("taskId") String taskId);

    int upsert(@Param("snapshot") ResearchTaskSnapshotEntity snapshot);
}
