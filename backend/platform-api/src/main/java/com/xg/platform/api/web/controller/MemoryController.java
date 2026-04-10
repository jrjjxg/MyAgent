package com.xg.platform.api.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.memory.CreateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryType;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.memory.UpdateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.UpsertUserProfileMemoryRequest;
import com.xg.platform.contracts.memory.UserProfileMemoryRecord;
import com.xg.platform.memory.application.LongTermMemoryKeyRegistry;
import com.xg.platform.memory.application.LongTermMemoryMaintenanceService;
import com.xg.platform.memory.port.LongTermMemoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
public class MemoryController {

    private static final String PROFILE_TITLE = "User profile";

    private final ConversationMemoryService conversationMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final LongTermMemoryMaintenanceService longTermMemoryMaintenanceService;
    private final ObjectMapper objectMapper;

    public MemoryController(ConversationMemoryService conversationMemoryService,
                            LongTermMemoryRepository longTermMemoryRepository,
                            LongTermMemoryMaintenanceService longTermMemoryMaintenanceService,
                            ObjectMapper objectMapper) {
        this.conversationMemoryService = conversationMemoryService;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.longTermMemoryMaintenanceService = longTermMemoryMaintenanceService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/threads/{threadId}/memory")
    public ThreadMemoryView threadMemory(@CurrentUserId String userId,
                                         @PathVariable String threadId) {
        return conversationMemoryService.threadMemoryView(userId, threadId);
    }

    @GetMapping("/memory/long-term")
    public List<LongTermMemoryRecord> listLongTerm(@CurrentUserId String userId) {
        return longTermMemoryRepository.listActive(userId);
    }

    @PostMapping("/memory/long-term")
    public LongTermMemoryRecord createLongTerm(@CurrentUserId String userId,
                                               @RequestBody CreateLongTermMemoryRequest request) {
        return longTermMemoryRepository.create(userId, request);
    }

    @PutMapping("/memory/long-term/{memoryId}")
    public LongTermMemoryRecord updateLongTerm(@CurrentUserId String userId,
                                               @PathVariable String memoryId,
                                               @RequestBody UpdateLongTermMemoryRequest request) {
        return longTermMemoryRepository.update(userId, memoryId, request);
    }

    @DeleteMapping("/memory/long-term/{memoryId}")
    public ResponseEntity<Void> deleteLongTerm(@CurrentUserId String userId,
                                               @PathVariable String memoryId) {
        longTermMemoryRepository.delete(userId, memoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/memory/long-term/cleanup")
    public CleanupResponse cleanupLongTerm(@CurrentUserId String userId) {
        LongTermMemoryMaintenanceService.CleanupResult result = longTermMemoryMaintenanceService.cleanupUserMemories(userId);
        return new CleanupResponse(result.processed(), result.rewritten(), result.deleted());
    }

    @GetMapping("/memory/profile")
    public ResponseEntity<UserProfileMemoryRecord> getProfileMemory(@CurrentUserId String userId) {
        return findStructuredProfileMemory(userId)
                .map(memory -> ResponseEntity.ok(toProfileRecord(userId, memory)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/memory/profile")
    public UserProfileMemoryRecord upsertProfileMemory(@CurrentUserId String userId,
                                                       @RequestBody UpsertUserProfileMemoryRequest request) {
        StoredUserProfileMemoryPayload payload = normalizeProfilePayload(request);
        JsonNode valueJson = objectMapper.valueToTree(payload);
        String summary = renderProfileSummary(payload);
        LongTermMemoryRecord stored = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.PROFILE,
                LongTermMemoryKeyRegistry.PROFILE_USER,
                PROFILE_TITLE,
                summary,
                valueJson,
                null,
                null,
                null
        ));
        return toProfileRecord(userId, stored);
    }

    private Optional<LongTermMemoryRecord> findStructuredProfileMemory(String userId) {
        return longTermMemoryRepository.findActiveByCanonicalKey(
                userId,
                LongTermMemoryType.PROFILE,
                LongTermMemoryKeyRegistry.PROFILE_USER
        );
    }

    private UserProfileMemoryRecord toProfileRecord(String userId, LongTermMemoryRecord memory) {
        StoredUserProfileMemoryPayload payload;
        try {
            payload = objectMapper.treeToValue(memory.valueJson(), StoredUserProfileMemoryPayload.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Profile memory payload is invalid", exception);
        }
        return new UserProfileMemoryRecord(
                userId,
                trimToNull(payload.displayName()),
                trimToNull(payload.preferredLanguage()),
                normalizedList(payload.preferredOutputStyles()),
                normalizedList(payload.projectTags()),
                trimToNull(payload.notes()),
                memory.content(),
                memory.updatedAt()
        );
    }

    private StoredUserProfileMemoryPayload normalizeProfilePayload(UpsertUserProfileMemoryRequest request) {
        return new StoredUserProfileMemoryPayload(
                trimToNull(request.displayName()),
                trimToNull(request.preferredLanguage()),
                normalizedList(request.preferredOutputStyles()),
                normalizedList(request.projectTags()),
                trimToNull(request.notes())
        );
    }

    private String renderProfileSummary(StoredUserProfileMemoryPayload payload) {
        List<String> parts = new ArrayList<>();
        if (payload.displayName() != null) {
            parts.add("Display name: " + payload.displayName());
        }
        if (payload.preferredLanguage() != null) {
            parts.add("Preferred language: " + payload.preferredLanguage());
        }
        if (!payload.preferredOutputStyles().isEmpty()) {
            parts.add("Preferred output styles: " + String.join(", ", payload.preferredOutputStyles()));
        }
        if (!payload.projectTags().isEmpty()) {
            parts.add("Project tags: " + String.join(", ", payload.projectTags()));
        }
        if (payload.notes() != null) {
            parts.add(payload.notes());
        }
        return String.join("\n", parts);
    }

    private List<String> normalizedList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::trimToNull)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private record StoredUserProfileMemoryPayload(
            String displayName,
            String preferredLanguage,
            List<String> preferredOutputStyles,
            List<String> projectTags,
            String notes
    ) {
    }

    private record CleanupResponse(int processed, int rewritten, int deleted) {
    }
}
