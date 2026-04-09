package com.xg.platform.contracts.memory;

import java.util.List;

public record UpsertUserProfileMemoryRequest(
        String displayName,
        String preferredLanguage,
        List<String> preferredOutputStyles,
        List<String> projectTags,
        String notes
) {
}
