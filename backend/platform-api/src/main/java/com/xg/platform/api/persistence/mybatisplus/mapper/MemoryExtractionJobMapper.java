package com.xg.platform.api.persistence.mybatisplus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xg.platform.api.persistence.mybatisplus.entity.MemoryExtractionJobEntity;
import org.apache.ibatis.annotations.Param;

public interface MemoryExtractionJobMapper extends BaseMapper<MemoryExtractionJobEntity> {

    MemoryExtractionJobEntity insertQueuedIfAbsent(@Param("job") MemoryExtractionJobEntity job);

    MemoryExtractionJobEntity findLatestSucceeded(@Param("userId") String userId,
                                                  @Param("threadId") String threadId,
                                                  @Param("extractorVersion") String extractorVersion);

    int countPendingJobs(@Param("userId") String userId,
                         @Param("threadId") String threadId,
                         @Param("extractorVersion") String extractorVersion);

    int markRunning(@Param("jobId") String jobId,
                    @Param("status") String status,
                    @Param("updatedAt") java.time.Instant updatedAt,
                    @Param("expectedStatus") String expectedStatus);

    int markSucceeded(@Param("jobId") String jobId,
                      @Param("status") String status,
                      @Param("updatedAt") java.time.Instant updatedAt);

    int markFailure(@Param("jobId") String jobId,
                    @Param("status") String status,
                    @Param("lastError") String lastError,
                    @Param("updatedAt") java.time.Instant updatedAt,
                    @Param("completedAt") java.time.Instant completedAt);
}
