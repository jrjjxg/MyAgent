package com.xg.platform.contracts.validation;

import java.nio.file.Path;
import java.util.regex.Pattern;

public final class PlatformIds {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    private PlatformIds() {
    }

    public static String requireUserId(String userId) {
        return requireIdentifier(userId, "userId");
    }

    public static String requireThreadId(String threadId) {
        return requireIdentifier(threadId, "threadId");
    }

    public static String requireWorkspaceId(String workspaceId) {
        return requireIdentifier(workspaceId, "workspaceId");
    }

    public static String requireIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        String trimmed = value.trim();
        if (!SAFE_IDENTIFIER.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(fieldName + " contains unsupported characters");
        }
        return trimmed;
    }

    public static Path requireRelativePath(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        Path normalized = Path.of(value).normalize();
        if (normalized.isAbsolute() || normalized.getNameCount() == 0 || normalized.startsWith("..")) {
            throw new IllegalArgumentException(fieldName + " must stay inside the thread workspace");
        }
        for (Path segment : normalized) {
            String part = segment.toString();
            if (".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException(fieldName + " must stay inside the thread workspace");
            }
        }
        return normalized;
    }
}
