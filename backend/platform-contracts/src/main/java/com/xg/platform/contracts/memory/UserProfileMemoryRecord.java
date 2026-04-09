package com.xg.platform.contracts.memory;

import java.time.Instant;
import java.util.List;

public record UserProfileMemoryRecord(
        String userId,
        String displayName,
        String preferredLanguage,
        List<String> preferredOutputStyles,
        List<String> projectTags,
        String notes,
        String content,
        Instant updatedAt
) {
}
