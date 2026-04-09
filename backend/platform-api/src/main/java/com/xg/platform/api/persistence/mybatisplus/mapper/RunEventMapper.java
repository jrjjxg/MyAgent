package com.xg.platform.api.persistence.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.RunEventEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RunEventMapper extends BaseMapper<RunEventEntity> {

    int insertEvent(@Param("event") RunEventEntity event);

    List<RunEventEntity> listByRunId(@Param("userId") String userId,
                                     @Param("threadId") String threadId,
                                     @Param("runId") String runId);

    List<RunEventEntity> listLatest(@Param("userId") String userId,
                                    @Param("threadId") String threadId,
                                    @Param("limit") int limit);
}
