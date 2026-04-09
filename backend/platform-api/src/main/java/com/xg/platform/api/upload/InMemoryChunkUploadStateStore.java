package com.xg.platform.api.upload;

import java.time.Instant;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryChunkUploadStateStore implements ChunkUploadStateStore {

    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    @Override
    public ChunkUploadSession create(ChunkUploadSession session) {
        sessions.put(key(session.userId(), session.workspaceId(), session.uploadId()), new SessionEntry(session));
        return session;
    }

    @Override
    public Optional<ChunkUploadSession> find(String userId, String workspaceId, String uploadId) {
        SessionEntry entry = sessions.get(key(userId, workspaceId, uploadId));
        if (entry == null) {
            return Optional.empty();
        }
        synchronized (entry) {
            return Optional.of(entry.session);
        }
    }

    @Override
    public int countUploadedChunks(String userId, String workspaceId, String uploadId) {
        SessionEntry entry = getRequiredEntry(userId, workspaceId, uploadId);
        synchronized (entry) {
            return entry.uploadedChunks;
        }
    }

    @Override
    public ChunkUploadProgress markChunkUploaded(String userId, String workspaceId, String uploadId, int chunkIndex) {
        SessionEntry entry = getRequiredEntry(userId, workspaceId, uploadId);
        synchronized (entry) {
            boolean alreadyUploaded = entry.bitmap.get(chunkIndex);
            if (!alreadyUploaded) {
                entry.bitmap.set(chunkIndex);
                entry.uploadedChunks++;
            }
            entry.session = entry.session.touch(Instant.now());
            return new ChunkUploadProgress(entry.session, entry.uploadedChunks, alreadyUploaded);
        }
    }

    @Override
    public ChunkUploadSession markCompleted(String userId,
                                            String workspaceId,
                                            String uploadId,
                                            String artifactId,
                                            String documentId,
                                            String ingestTaskId) {
        SessionEntry entry = getRequiredEntry(userId, workspaceId, uploadId);
        synchronized (entry) {
            entry.session = entry.session.markCompleted(artifactId, documentId, ingestTaskId, Instant.now());
            return entry.session;
        }
    }

    private SessionEntry getRequiredEntry(String userId, String workspaceId, String uploadId) {
        SessionEntry entry = sessions.get(key(userId, workspaceId, uploadId));
        if (entry == null) {
            throw new IllegalArgumentException("Upload session not found: " + uploadId);
        }
        return entry;
    }

    private String key(String userId, String workspaceId, String uploadId) {
        return userId + ":" + workspaceId + ":" + uploadId;
    }

    private static final class SessionEntry {

        private final BitSet bitmap = new BitSet();
        private ChunkUploadSession session;
        private int uploadedChunks;

        private SessionEntry(ChunkUploadSession session) {
            this.session = session;
        }
    }
}
