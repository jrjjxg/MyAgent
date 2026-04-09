package com.xg.platform.runtime;

import com.xg.platform.contracts.memory.CreateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.UpdateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.LongTermMemoryType;

import java.util.List;
import java.util.Optional;

public interface LongTermMemoryRepository {

    List<LongTermMemoryRecord> listActive(String userId);

    Optional<LongTermMemoryRecord> findById(String userId, String memoryId);

    Optional<LongTermMemoryRecord> findActiveByTitle(String userId, String title);

    Optional<LongTermMemoryRecord> findActiveByCanonicalKey(String userId, LongTermMemoryType memoryType, String canonicalKey);

    LongTermMemoryRecord create(String userId, CreateLongTermMemoryRequest request);

    LongTermMemoryRecord update(String userId, String memoryId, UpdateLongTermMemoryRequest request);

    void delete(String userId, String memoryId);

    int deleteBySourceThread(String userId, String sourceThreadId);
}
