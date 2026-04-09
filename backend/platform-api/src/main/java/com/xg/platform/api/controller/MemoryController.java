package com.xg.platform.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xg.platform.agent.core.application.ConversationMemoryService;
import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.contracts.memory.CreateStableFactRequest;
import com.xg.platform.contracts.memory.CreateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.LongTermMemoryRecord;
import com.xg.platform.contracts.memory.LongTermMemoryType;
import com.xg.platform.contracts.memory.StableFactMemoryRecord;
import com.xg.platform.contracts.memory.ThreadMemoryView;
import com.xg.platform.contracts.memory.UpdateStableFactRequest;
import com.xg.platform.contracts.memory.UpdateLongTermMemoryRequest;
import com.xg.platform.contracts.memory.UpsertUserProfileMemoryRequest;
import com.xg.platform.contracts.memory.UserProfileMemoryRecord;
import com.xg.platform.runtime.LongTermMemoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class MemoryController {

    private static final String PROFILE_CANONICAL_KEY = "profile.user";
    private static final String PROFILE_TITLE = "User profile";
    private static final String FACT_PREFIX = "semantic.fact.";
    private static final String FACT_TITLE_MARKER = ".title.";
    private static final String FACT_ID_MARKER = ".id.";

    private final ConversationMemoryService conversationMemoryService;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final ObjectMapper objectMapper;

    public MemoryController(ConversationMemoryService conversationMemoryService,
                            LongTermMemoryRepository longTermMemoryRepository,
                            ObjectMapper objectMapper) {
        this.conversationMemoryService = conversationMemoryService;
        this.longTermMemoryRepository = longTermMemoryRepository;
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

    @GetMapping("/memory/profile")
    public ResponseEntity<UserProfileMemoryRecord> getProfileMemory(@CurrentUserId String userId) {
        return findProfileMemory(userId)
                .map(memory -> ResponseEntity.ok(toProfileRecord(userId, memory)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/memory/profile")
    public UserProfileMemoryRecord upsertProfileMemory(@CurrentUserId String userId,
                                                       @RequestBody UpsertUserProfileMemoryRequest request) {
        StoredUserProfileMemoryPayload payload = normalizeProfilePayload(request);
        String serialized = writeProfilePayload(payload);
        LongTermMemoryRecord stored = findStructuredProfileMemory(userId)
                .map(existing -> longTermMemoryRepository.update(userId, existing.memoryId(), new UpdateLongTermMemoryRequest(
                        LongTermMemoryType.PROFILE,
                        PROFILE_CANONICAL_KEY,
                        PROFILE_TITLE,
                        serialized,
                        null,
                        null,
                        null
                )))
                .orElseGet(() -> longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                        LongTermMemoryType.PROFILE,
                        PROFILE_CANONICAL_KEY,
                        PROFILE_TITLE,
                        serialized,
                        null,
                        null,
                        null
                )));
        return toProfileRecord(userId, stored);
    }

    @GetMapping("/memory/facts")
    public List<StableFactMemoryRecord> listStableFacts(@CurrentUserId String userId) {
        return longTermMemoryRepository.listActive(userId).stream()
                .filter(memory -> memory.memoryType() == LongTermMemoryType.SEMANTIC)
                .map(this::toStableFactRecord)
                .toList();
    }

    @PostMapping("/memory/facts")
    public StableFactMemoryRecord createStableFact(@CurrentUserId String userId,
                                                   @RequestBody CreateStableFactRequest request) {
        LongTermMemoryRecord stored = longTermMemoryRepository.create(userId, new CreateLongTermMemoryRequest(
                LongTermMemoryType.SEMANTIC,
                buildFactCanonicalKey(request.factType(), request.title(), null),
                stableFactTitle(request.title(), request.content()),
                stableFactContent(request.content(), request.title()),
                trimToNull(request.sourceThreadId()),
                null,
                trimToNull(request.sourceTaskId())
        ));
        return toStableFactRecord(stored);
    }

    @PutMapping("/memory/facts/{memoryId}")
    public StableFactMemoryRecord updateStableFact(@CurrentUserId String userId,
                                                   @PathVariable String memoryId,
                                                   @RequestBody UpdateStableFactRequest request) {
        LongTermMemoryRecord existing = longTermMemoryRepository.findById(userId, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Stable fact not found: " + memoryId));
        LongTermMemoryRecord stored = longTermMemoryRepository.update(userId, memoryId, new UpdateLongTermMemoryRequest(
                LongTermMemoryType.SEMANTIC,
                buildFactCanonicalKey(request.factType(), request.title(), existing.canonicalKey()),
                stableFactTitle(request.title(), request.content()),
                stableFactContent(request.content(), request.title()),
                request.sourceThreadId(),
                null,
                request.sourceTaskId()
        ));
        return toStableFactRecord(stored);
    }

    @DeleteMapping("/memory/facts/{memoryId}")
    public ResponseEntity<Void> deleteStableFact(@CurrentUserId String userId,
                                                 @PathVariable String memoryId) {
        longTermMemoryRepository.delete(userId, memoryId);
        return ResponseEntity.noContent().build();
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

    private Optional<LongTermMemoryRecord> findProfileMemory(String userId) {
        Optional<LongTermMemoryRecord> structured = findStructuredProfileMemory(userId);
        if (structured.isPresent()) {
            return structured;
        }
        return longTermMemoryRepository.listActive(userId).stream()
                .filter(memory -> memory.memoryType() == LongTermMemoryType.PROFILE)
                .findFirst();
    }

    private Optional<LongTermMemoryRecord> findStructuredProfileMemory(String userId) {
        return longTermMemoryRepository.findActiveByCanonicalKey(userId, LongTermMemoryType.PROFILE, PROFILE_CANONICAL_KEY);
    }

    private UserProfileMemoryRecord toProfileRecord(String userId, LongTermMemoryRecord memory) {
        StoredUserProfileMemoryPayload payload = readProfilePayload(memory);
        String summary = renderProfileSummary(payload, memory.content());
        return new UserProfileMemoryRecord(
                userId,
                trimToNull(payload.displayName()),
                trimToNull(payload.preferredLanguage()),
                normalizedList(payload.preferredOutputStyles()),
                normalizedList(payload.projectTags()),
                trimToNull(payload.notes()),
                summary,
                memory.updatedAt()
        );
    }

    private StableFactMemoryRecord toStableFactRecord(LongTermMemoryRecord memory) {
        String factType = extractFactType(memory.canonicalKey());
        String category = humanizeToken(factType);
        String fact = trimToNull(memory.content()) == null ? trimToNull(memory.title()) : memory.content().trim();
        return new StableFactMemoryRecord(
                memory.memoryId(),
                memory.userId(),
                factType,
                category,
                trimToNull(memory.title()),
                trimToNull(memory.content()),
                fact,
                trimToNull(memory.sourceThreadId()),
                trimToNull(memory.sourceTaskId()),
                memory.status(),
                memory.createdAt(),
                memory.updatedAt()
        );
    }

    private StoredUserProfileMemoryPayload normalizeProfilePayload(UpsertUserProfileMemoryRequest request) {
        if (request == null) {
            return new StoredUserProfileMemoryPayload(null, null, List.of(), List.of(), null);
        }
        return new StoredUserProfileMemoryPayload(
                trimToNull(request.displayName()),
                trimToNull(request.preferredLanguage()),
                normalizedList(request.preferredOutputStyles()),
                normalizedList(request.projectTags()),
                trimToNull(request.notes())
        );
    }

    private StoredUserProfileMemoryPayload readProfilePayload(LongTermMemoryRecord memory) {
        if (memory == null) {
            return new StoredUserProfileMemoryPayload(null, null, List.of(), List.of(), null);
        }
        if (PROFILE_CANONICAL_KEY.equals(memory.canonicalKey()) && memory.content() != null && !memory.content().isBlank()) {
            try {
                return objectMapper.readValue(memory.content(), StoredUserProfileMemoryPayload.class);
            } catch (JsonProcessingException ignored) {
                return new StoredUserProfileMemoryPayload(null, null, List.of(), List.of(), trimToNull(memory.content()));
            }
        }
        return new StoredUserProfileMemoryPayload(null, null, List.of(), List.of(), trimToNull(memory.content()));
    }

    private String writeProfilePayload(StoredUserProfileMemoryPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize profile memory", exception);
        }
    }

    private String renderProfileSummary(StoredUserProfileMemoryPayload payload, String fallbackContent) {
        List<String> parts = new ArrayList<>();
        if (trimToNull(payload.displayName()) != null) {
            parts.add("Display name: " + payload.displayName().trim());
        }
        if (trimToNull(payload.preferredLanguage()) != null) {
            parts.add("Preferred language: " + payload.preferredLanguage().trim());
        }
        if (!normalizedList(payload.preferredOutputStyles()).isEmpty()) {
            parts.add("Preferred output styles: " + String.join(", ", normalizedList(payload.preferredOutputStyles())));
        }
        if (!normalizedList(payload.projectTags()).isEmpty()) {
            parts.add("Project tags: " + String.join(", ", normalizedList(payload.projectTags())));
        }
        if (trimToNull(payload.notes()) != null) {
            parts.add(payload.notes().trim());
        }
        if (!parts.isEmpty()) {
            return String.join("\n", parts);
        }
        return trimToNull(fallbackContent);
    }

    private String buildFactCanonicalKey(String factType, String title, String existingCanonicalKey) {
        String normalizedFactType = normalizeToken(factType);
        String normalizedTitle = normalizeToken(title);
        String suffix = extractFactIdSuffix(existingCanonicalKey);
        if (suffix == null) {
            suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        return FACT_PREFIX
                + (normalizedFactType == null ? "general" : normalizedFactType)
                + FACT_TITLE_MARKER
                + (normalizedTitle == null ? "memory" : normalizedTitle)
                + FACT_ID_MARKER
                + suffix;
    }

    private String extractFactType(String canonicalKey) {
        String trimmed = trimToNull(canonicalKey);
        if (trimmed == null) {
            return "general";
        }
        if (trimmed.startsWith(FACT_PREFIX)) {
            int titleIndex = trimmed.indexOf(FACT_TITLE_MARKER, FACT_PREFIX.length());
            if (titleIndex > FACT_PREFIX.length()) {
                return trimmed.substring(FACT_PREFIX.length(), titleIndex);
            }
        }
        if (trimmed.startsWith("semantic.")) {
            String remainder = trimmed.substring("semantic.".length());
            int segmentEnd = remainder.indexOf('.');
            return segmentEnd > 0 ? remainder.substring(0, segmentEnd) : remainder;
        }
        return "general";
    }

    private String extractFactIdSuffix(String canonicalKey) {
        String trimmed = trimToNull(canonicalKey);
        if (trimmed == null) {
            return null;
        }
        int idIndex = trimmed.indexOf(FACT_ID_MARKER);
        if (idIndex < 0) {
            return null;
        }
        String suffix = trimmed.substring(idIndex + FACT_ID_MARKER.length()).trim();
        return suffix.isBlank() ? null : suffix;
    }

    private String stableFactTitle(String title, String content) {
        String trimmedTitle = trimToNull(title);
        if (trimmedTitle != null) {
            return trimmedTitle;
        }
        String fallback = trimToNull(content);
        if (fallback == null) {
            return "Stable fact";
        }
        return fallback.length() <= 80 ? fallback : fallback.substring(0, 77) + "...";
    }

    private String stableFactContent(String content, String title) {
        String trimmedContent = trimToNull(content);
        if (trimmedContent != null) {
            return trimmedContent;
        }
        String trimmedTitle = trimToNull(title);
        return trimmedTitle == null ? "" : trimmedTitle;
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

    private String normalizeToken(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String normalized = trimmed.toLowerCase()
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String humanizeToken(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.replace('.', ' ');
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
}
