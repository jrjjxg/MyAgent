package com.xg.platform.api.persistence.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.ThreadMemorySnapshotEntity;
import org.apache.ibatis.annotations.Param;

public interface ThreadMemorySnapshotMapper extends BaseMapper<ThreadMemorySnapshotEntity> {

    ThreadMemorySnapshotEntity findByThread(@Param("userId") String userId, @Param("threadId") String threadId);

    int upsert(@Param("snapshot") ThreadMemorySnapshotEntity snapshot);
}
