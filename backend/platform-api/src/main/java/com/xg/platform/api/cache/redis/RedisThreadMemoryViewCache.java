package com.xg.platform.api.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.contracts.memory.CachedThreadMemoryRecord;
import com.xg.platform.runtime.ThreadMemoryViewCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedisThreadMemoryViewCache implements ThreadMemoryViewCache {

    private static final Logger logger = Logger.getLogger(RedisThreadMemoryViewCache.class.getName());

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisThreadMemoryViewCache(StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<CachedThreadMemoryRecord> get(String userId, String threadId) {
        String serialized;
        try {
            serialized = redisTemplate.opsForValue().get(key(userId, threadId));
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Thread memory cache read failed, falling back to source of truth", exception);
            return Optional.empty();
        }
        if (serialized == null || serialized.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(serialized, CachedThreadMemoryRecord.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize cached thread memory", exception);
        }
    }

    @Override
    public void put(String userId, CachedThreadMemoryRecord record) {
        if (record == null || record.threadId() == null || record.threadId().isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    key(userId, record.threadId()),
                    objectMapper.writeValueAsString(record),
                    ttl
            );
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Thread memory cache write failed, continuing without Redis cache", exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize cached thread memory", exception);
        }
    }

    private String key(String userId, String threadId) {
        return "memory:thread:%s:%s:view".formatted(userId, threadId);
    }
}
