package com.xg.platform.memory.application;

import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.UpdateLongTermMemoryRequest;
import com.xg.platform.memory.port.LongTermMemoryRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LongTermMemoryMaintenanceService {

    private final LongTermMemoryRepository longTermMemoryRepository;

    public LongTermMemoryMaintenanceService(LongTermMemoryRepository longTermMemoryRepository) {
        this.longTermMemoryRepository = longTermMemoryRepository;
    }

    public CleanupResult cleanupUserMemories(String userId) {
        List<LongTermMemoryRecord> activeMemories = longTermMemoryRepository.listActive(userId);
        Map<MemoryIdentity, List<LongTermMemoryRecord>> grouped = new LinkedHashMap<>();
        Map<String, LongTermMemoryKeyRegistry.NormalizedMemory> normalizedById = new LinkedHashMap<>();
        for (LongTermMemoryRecord memory : activeMemories) {
            String normalizedKey = LongTermMemoryKeyRegistry.normalizeStoredKey(
                    memory.memoryType(),
                    memory.canonicalKey(),
                    memory.title(),
                    memory.sourceMessageId()
            );
            if (normalizedKey == null) {
                continue;
            }
            LongTermMemoryKeyRegistry.NormalizedMemory normalizedMemory =
                    new LongTermMemoryKeyRegistry.NormalizedMemory(memory.memoryType(), normalizedKey);
            normalizedById.put(memory.memoryId(), normalizedMemory);
            grouped.computeIfAbsent(
                    new MemoryIdentity(normalizedMemory.memoryType(), normalizedMemory.canonicalKey()),
                    ignored -> new ArrayList<>()
            ).add(memory);
        }

        int rewritten = 0;
        int deleted = 0;
        for (List<LongTermMemoryRecord> group : grouped.values()) {
            group.sort(Comparator.comparing(LongTermMemoryRecord::updatedAt)
                    .thenComparing(LongTermMemoryRecord::createdAt)
                    .reversed());
            LongTermMemoryRecord keeper = group.get(0);
            for (int index = 1; index < group.size(); index++) {
                longTermMemoryRepository.delete(userId, group.get(index).memoryId());
                deleted++;
            }
            LongTermMemoryKeyRegistry.NormalizedMemory normalizedMemory = normalizedById.get(keeper.memoryId());
            if (normalizedMemory == null) {
                continue;
            }
            if (keeper.memoryType() != normalizedMemory.memoryType()
                    || !Objects.equals(keeper.canonicalKey(), normalizedMemory.canonicalKey())) {
                longTermMemoryRepository.update(userId, keeper.memoryId(), new UpdateLongTermMemoryRequest(
                        normalizedMemory.memoryType(),
                        normalizedMemory.canonicalKey(),
                        keeper.title(),
                        keeper.content(),
                        keeper.valueJson(),
                        keeper.sourceThreadId(),
                        keeper.sourceMessageId(),
                        keeper.sourceTaskId()
                ));
                rewritten++;
            }
        }

        return new CleanupResult(activeMemories.size(), rewritten, deleted);
    }

    public record CleanupResult(int processed, int rewritten, int deleted) {
    }

    private record MemoryIdentity(com.xg.platform.contracts.memory.LongTermMemoryType memoryType, String canonicalKey) {
    }
}
