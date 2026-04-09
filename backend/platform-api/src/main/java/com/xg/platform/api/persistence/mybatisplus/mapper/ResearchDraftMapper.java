package com.xg.platform.api.persistence.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.ResearchDraftEntity;
import org.apache.ibatis.annotations.Param;

public interface ResearchDraftMapper extends BaseMapper<ResearchDraftEntity> {

    ResearchDraftEntity findActiveDraft(@Param("userId") String userId, @Param("threadId") String threadId);

    int upsert(@Param("draft") ResearchDraftEntity draft);

    int clearActiveDrafts(@Param("userId") String userId, @Param("threadId") String threadId);
}
