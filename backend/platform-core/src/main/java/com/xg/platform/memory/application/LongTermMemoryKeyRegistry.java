package com.xg.platform.memory.application;

import com.xg.platform.contracts.memory.LongTermMemoryType;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LongTermMemoryKeyRegistry {

    public static final String PROFILE_USER = "profile.user";
    public static final String PROFILE_PREFERRED_LANGUAGE = "profile.preferred_language";
    public static final String PROFILE_OUTPUT_STYLE = "profile.output_style";
    public static final String PROFILE_AUDIENCE = "profile.audience";
    public static final String PROFILE_EXPERTISE_PREFIX = "profile.expertise.";

    public static final String PROCEDURE_ANSWER_STYLE = "procedure.answer_style";
    public static final String PROCEDURE_LANGUAGE = "procedure.language";
    public static final String PROCEDURE_CODE_STYLE = "procedure.code_style";
    public static final String PROCEDURE_RESEARCH_STYLE = "procedure.research_style";
    public static final String PROCEDURE_WORKFLOW = "procedure.workflow";

    private static final Set<String> PROFILE_KEYS = Set.of(
            PROFILE_USER,
            PROFILE_PREFERRED_LANGUAGE,
            PROFILE_OUTPUT_STYLE,
            PROFILE_AUDIENCE
    );
    private static final Set<String> PROCEDURAL_KEYS = Set.of(
            PROCEDURE_ANSWER_STYLE,
            PROCEDURE_LANGUAGE,
            PROCEDURE_CODE_STYLE,
            PROCEDURE_RESEARCH_STYLE,
            PROCEDURE_WORKFLOW
    );
    private static final Set<String> SEMANTIC_PREFIXES = Set.of(
            "semantic.project.",
            "semantic.research_topic.",
            "semantic.preference.",
            "semantic.skill_level.",
            "semantic.domain.",
            "semantic.misc."
    );
    private static final Map<String, String> SEMANTIC_ALIASES = Map.ofEntries(
            Map.entry("research.context", "semantic.research_topic.primary"),
            Map.entry("research.area", "semantic.research_topic.primary"),
            Map.entry("research.topic", "semantic.research_topic.primary"),
            Map.entry("research.interest", "semantic.research_topic.primary"),
            Map.entry("investment.preference", "semantic.preference.investment"),
            Map.entry("investment.preferences", "semantic.preference.investment"),
            Map.entry("investment.interests", "semantic.preference.investment"),
            Map.entry("financial.knowledge", "semantic.skill_level.finance"),
            Map.entry("stock.monitoring.project", "semantic.project.stock_monitoring"),
            Map.entry("stock.monitoring.environment", "semantic.project.stock_monitoring"),
            Map.entry("stock.monitoring.system", "semantic.project.stock_monitoring"),
            Map.entry("output.style", "semantic.preference.output_style")
    );
    private static final Map<String, String> PROFILE_ALIASES = Map.ofEntries(
            Map.entry("user.profile", PROFILE_USER),
            Map.entry("preferred.language", PROFILE_PREFERRED_LANGUAGE),
            Map.entry("language.preference", PROFILE_PREFERRED_LANGUAGE),
            Map.entry("output.style", PROFILE_OUTPUT_STYLE),
            Map.entry("audience", PROFILE_AUDIENCE)
    );
    private static final Map<String, String> PROCEDURAL_ALIASES = Map.ofEntries(
            Map.entry("answer.style", PROCEDURE_ANSWER_STYLE),
            Map.entry("language", PROCEDURE_LANGUAGE),
            Map.entry("code.style", PROCEDURE_CODE_STYLE),
            Map.entry("research.style", PROCEDURE_RESEARCH_STYLE),
            Map.entry("workflow", PROCEDURE_WORKFLOW)
    );

    private LongTermMemoryKeyRegistry() {
    }

    public static NormalizedMemory normalizeForWrite(LongTermMemoryType requestedType,
                                                     String candidateKey,
                                                     String title,
                                                     String sourceMessageId) {
        LongTermMemoryType memoryType = requestedType == null ? LongTermMemoryType.SEMANTIC : requestedType;
        String canonicalKey = switch (memoryType) {
            case PROFILE -> normalizeProfileKey(candidateKey, title);
            case SEMANTIC -> normalizeSemanticKey(candidateKey, title);
            case EPISODIC -> normalizeEpisodicKey(candidateKey, title, sourceMessageId);
            case PROCEDURAL -> normalizeProceduralKey(candidateKey, title);
        };
        if (canonicalKey == null) {
            throw new IllegalArgumentException("Unsupported long-term memory key");
        }
        return new NormalizedMemory(memoryType, canonicalKey);
    }

    public static String normalizeStoredKey(LongTermMemoryType requestedType,
                                            String candidateKey,
                                            String title,
                                            String sourceMessageId) {
        LongTermMemoryType memoryType = requestedType == null ? LongTermMemoryType.SEMANTIC : requestedType;
        return switch (memoryType) {
            case PROFILE -> normalizeProfileKey(candidateKey, title);
            case SEMANTIC -> normalizeSemanticKey(candidateKey, title);
            case EPISODIC -> sourceMessageId == null || sourceMessageId.isBlank()
                    ? null
                    : normalizeEpisodicKey(candidateKey, title, sourceMessageId);
            case PROCEDURAL -> normalizeProceduralKey(candidateKey, title);
        };
    }

    public static String extractionKeyGuidance() {
        return """
                Allowed canonical keys:
                - PROFILE: profile.user, profile.preferred_language, profile.output_style, profile.audience, profile.expertise.<domain>
                - SEMANTIC: semantic.project.<slug>, semantic.research_topic.<slug>, semantic.preference.<slug>, semantic.skill_level.<slug>, semantic.domain.<slug>, semantic.misc.<slug>
                - EPISODIC: episode.<category>.<source_message_id>
                - PROCEDURAL: procedure.answer_style, procedure.language, procedure.code_style, procedure.research_style, procedure.workflow
                """;
    }

    private static String normalizeProfileKey(String candidateKey, String title) {
        String normalizedCandidate = normalizeToken(candidateKey);
        if (normalizedCandidate != null) {
            String aliased = PROFILE_ALIASES.get(normalizedCandidate);
            if (aliased != null) {
                return aliased;
            }
            if (PROFILE_KEYS.contains(normalizedCandidate) || normalizedCandidate.startsWith(PROFILE_EXPERTISE_PREFIX)) {
                return normalizedCandidate;
            }
        }
        String normalizedTitle = normalizeToken(title);
        if (normalizedTitle == null) {
            return null;
        }
        String aliased = PROFILE_ALIASES.get(normalizedTitle);
        if (aliased != null) {
            return aliased;
        }
        return normalizedTitle.startsWith(PROFILE_EXPERTISE_PREFIX) ? normalizedTitle : null;
    }

    private static String normalizeSemanticKey(String candidateKey, String title) {
        String aliasedTitle = aliasSemantic(title);
        if (aliasedTitle != null) {
            return aliasedTitle;
        }
        String normalizedCandidate = normalizeToken(candidateKey);
        if (normalizedCandidate != null) {
            String aliasedCandidate = aliasSemantic(normalizedCandidate);
            if (aliasedCandidate != null) {
                return aliasedCandidate;
            }
            if (normalizedCandidate.startsWith("semantic.fact.")) {
                return normalizeLegacyFactKey(normalizedCandidate, title);
            }
            if (hasAllowedSemanticPrefix(normalizedCandidate)) {
                return normalizedCandidate;
            }
            String semanticBody = normalizedCandidate.startsWith("semantic.")
                    ? normalizedCandidate.substring("semantic.".length())
                    : normalizedCandidate;
            return "semantic.misc." + semanticBody;
        }
        String normalizedTitle = normalizeToken(title);
        return normalizedTitle == null ? null : "semantic.misc." + normalizedTitle;
    }

    private static String normalizeEpisodicKey(String candidateKey, String title, String sourceMessageId) {
        String normalizedSourceMessageId = normalizeToken(sourceMessageId);
        if (normalizedSourceMessageId == null) {
            throw new IllegalArgumentException("Episodic memory requires sourceMessageId");
        }
        String category = normalizeToken(candidateKey);
        if (category != null && category.startsWith("episode.")) {
            category = category.substring("episode.".length());
            if (category.endsWith("." + normalizedSourceMessageId)) {
                category = category.substring(0, category.length() - normalizedSourceMessageId.length() - 1);
            }
        }
        if (category == null) {
            category = normalizeToken(title);
        }
        if (category == null) {
            throw new IllegalArgumentException("Episodic memory requires category");
        }
        return "episode." + category + "." + normalizedSourceMessageId;
    }

    private static String normalizeProceduralKey(String candidateKey, String title) {
        String normalizedCandidate = normalizeToken(candidateKey);
        if (normalizedCandidate != null) {
            String aliased = PROCEDURAL_ALIASES.get(normalizedCandidate);
            if (aliased != null) {
                return aliased;
            }
            if (PROCEDURAL_KEYS.contains(normalizedCandidate)) {
                return normalizedCandidate;
            }
        }
        String normalizedTitle = normalizeToken(title);
        return normalizedTitle == null ? null : PROCEDURAL_ALIASES.get(normalizedTitle);
    }

    private static String aliasSemantic(String value) {
        String normalized = normalizeToken(value);
        if (normalized == null) {
            return null;
        }
        return SEMANTIC_ALIASES.get(normalized);
    }

    private static String normalizeLegacyFactKey(String candidateKey, String title) {
        int titleStart = candidateKey.indexOf(".title.");
        String factType = titleStart < 0
                ? "misc"
                : candidateKey.substring("semantic.fact.".length(), titleStart);
        String titleSlug = normalizeToken(title);
        if (titleSlug == null && titleStart >= 0) {
            int idStart = candidateKey.indexOf(".id.", titleStart + 7);
            if (idStart > titleStart) {
                titleSlug = candidateKey.substring(titleStart + 7, idStart);
            }
        }
        if ("preference".equals(factType) && titleSlug != null) {
            String aliased = aliasSemantic(titleSlug);
            return aliased != null ? aliased : "semantic.preference." + titleSlug;
        }
        if ("project".equals(factType) && titleSlug != null) {
            String aliased = aliasSemantic(titleSlug);
            return aliased != null ? aliased : "semantic.project." + titleSlug;
        }
        if ("domain".equals(factType) && titleSlug != null) {
            return "semantic.domain." + titleSlug;
        }
        return titleSlug == null ? "semantic.misc.fact" : "semantic.misc." + titleSlug;
    }

    private static boolean hasAllowedSemanticPrefix(String value) {
        return SEMANTIC_PREFIXES.stream().anyMatch(value::startsWith);
    }

    public static String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");
        return normalized.isBlank() ? null : normalized;
    }

    public record NormalizedMemory(LongTermMemoryType memoryType, String canonicalKey) {
    }
}
