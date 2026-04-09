package com.xg.platform.api.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class RedisChunkUploadStateStore implements ChunkUploadStateStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisChunkUploadStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public ChunkUploadSession create(ChunkUploadSession session) {
        writeSession(session);
        redisTemplate.opsForValue().set(countKey(session.userId(), session.workspaceId(), session.uploadId()), "0", ttl);
        redisTemplate.delete(bitmapKey(session.userId(), session.workspaceId(), session.uploadId()));
        refreshTtl(session.userId(), session.workspaceId(), session.uploadId());
        return session;
    }

    @Override
    public Optional<ChunkUploadSession> find(String userId, String workspaceId, String uploadId) {
        String serialized = redisTemplate.opsForValue().get(metaKey(userId, workspaceId, uploadId));
        if (serialized == null || serialized.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(readSession(serialized));
    }

    @Override
    public int countUploadedChunks(String userId, String workspaceId, String uploadId) {
        String count = redisTemplate.opsForValue().get(countKey(userId, workspaceId, uploadId));
        if (count == null || count.isBlank()) {
            return 0;
        }
        return Integer.parseInt(count);
    }

    @Override
    public ChunkUploadProgress markChunkUploaded(String userId, String workspaceId, String uploadId, int chunkIndex) {
        ChunkUploadSession current = find(userId, workspaceId, uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload session not found: " + uploadId));
        Boolean previous = redisTemplate.opsForValue().setBit(bitmapKey(userId, workspaceId, uploadId), chunkIndex, true);
        int uploadedChunks;
        boolean alreadyUploaded = Boolean.TRUE.equals(previous);
        if (alreadyUploaded) {
            uploadedChunks = countUploadedChunks(userId, workspaceId, uploadId);
        } else {
            Long updated = redisTemplate.opsForValue().increment(countKey(userId, workspaceId, uploadId));
            uploadedChunks = updated == null ? countUploadedChunks(userId, workspaceId, uploadId) : updated.intValue();
        }
        ChunkUploadSession updatedSession = current.touch(Instant.now());
        writeSession(updatedSession);
        refreshTtl(userId, workspaceId, uploadId);
        return new ChunkUploadProgress(updatedSession, uploadedChunks, alreadyUploaded);
    }

    @Override
    public ChunkUploadSession markCompleted(String userId,
                                            String workspaceId,
                                            String uploadId,
                                            String artifactId,
                                            String documentId,
                                            String ingestTaskId) {
        ChunkUploadSession current = find(userId, workspaceId, uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload session not found: " + uploadId));
        ChunkUploadSession completed = current.markCompleted(artifactId, documentId, ingestTaskId, Instant.now());
        writeSession(completed);
        refreshTtl(userId, workspaceId, uploadId);
        return completed;
    }

    private void writeSession(ChunkUploadSession session) {
        try {
            redisTemplate.opsForValue().set(
                    metaKey(session.userId(), session.workspaceId(), session.uploadId()),
                    objectMapper.writeValueAsString(session),
                    ttl
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize upload session", exception);
        }
    }

    private ChunkUploadSession readSession(String serialized) {
        try {
            return objectMapper.readValue(serialized, ChunkUploadSession.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize upload session", exception);
        }
    }

    private void refreshTtl(String userId, String workspaceId, String uploadId) {
        redisTemplate.expire(metaKey(userId, workspaceId, uploadId), ttl);
        redisTemplate.expire(bitmapKey(userId, workspaceId, uploadId), ttl);
        redisTemplate.expire(countKey(userId, workspaceId, uploadId), ttl);
    }

    private String metaKey(String userId, String workspaceId, String uploadId) {
        return "upload:workspace:%s:%s:%s:meta".formatted(userId, workspaceId, uploadId);
    }

    private String bitmapKey(String userId, String workspaceId, String uploadId) {
        return "upload:workspace:%s:%s:%s:bitmap".formatted(userId, workspaceId, uploadId);
    }

    private String countKey(String userId, String workspaceId, String uploadId) {
        return "upload:workspace:%s:%s:%s:count".formatted(userId, workspaceId, uploadId);
    }
}
