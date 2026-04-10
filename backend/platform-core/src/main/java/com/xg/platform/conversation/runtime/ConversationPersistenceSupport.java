package com.xg.platform.conversation.runtime;

import com.xg.platform.agent.core.AgentGraphMessage;
import com.xg.platform.agent.core.AgentGraphMessageType;
import com.xg.platform.agent.core.ExecutionSource;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.MessageRole;
import com.xg.platform.contracts.conversation.PostMessageRequest;
import com.xg.platform.contracts.conversation.ThreadFileReference;
import com.xg.platform.contracts.memory.ThreadMemorySnapshotRecord;
import com.xg.platform.conversation.domain.ConversationRouteKind;
import com.xg.platform.conversation.port.MessageRepository;
import com.xg.platform.memory.port.ThreadMemorySnapshotRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ConversationPersistenceSupport {

    private static final String DEGRADED_PROPERTY = "degraded";
    private static final String DEGRADATION_REASON_PROPERTY = "degradationReason";

    private final ThreadMemorySnapshotRepository threadMemorySnapshotRepository;
    private final MessageRepository messageRepository;

    ConversationPersistenceSupport(ThreadMemorySnapshotRepository threadMemorySnapshotRepository,
                                   MessageRepository messageRepository) {
        this.threadMemorySnapshotRepository = threadMemorySnapshotRepository;
        this.messageRepository = messageRepository;
    }

    List<String> selectedDocumentIds(PostMessageRequest request) {
        if (request == null || request.documentIds() == null || request.documentIds().isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(request.documentIds()));
    }

    List<String> loadPersistedActiveSkillIds(String userId, String threadId) {
        return threadMemorySnapshotRepository.findByThread(userId, threadId)
                .map(snapshot -> normalizeSkillIds(snapshot.activeSkillIds()))
                .orElse(List.of());
    }

    void persistActiveSkillIds(String userId, String threadId, List<String> activeSkillIds) {
        List<String> normalizedSkillIds = normalizeSkillIds(activeSkillIds);
        ThreadMemorySnapshotRecord existing = threadMemorySnapshotRepository.findByThread(userId, threadId).orElse(null);
        threadMemorySnapshotRepository.save(userId, new ThreadMemorySnapshotRecord(
                threadId,
                userId,
                existing == null ? "" : existing.summary(),
                existing == null ? null : existing.lastCompactedMessageId(),
                existing == null ? List.of() : existing.pendingHistoricalMessages(),
                existing == null ? null : existing.recentEndMessageId(),
                existing == null ? 20 : existing.recentWindowSize(),
                existing == null ? null : existing.activeDraftId(),
                existing == null ? null : existing.activeTaskId(),
                existing == null ? null : existing.taskStage(),
                normalizedSkillIds,
                Instant.now()
        ));
    }

    List<ExecutionSource> deduplicateSources(List<ExecutionSource> sources) {
        Map<String, ExecutionSource> deduped = new LinkedHashMap<>();
        for (ExecutionSource source : sources) {
            ExecutionSource existing = deduped.get(source.url());
            if (existing == null || (!existing.verified() && source.verified())) {
                deduped.put(source.url(), source);
            }
        }
        return List.copyOf(deduped.values());
    }

    AgentGraphMessage findLastFinalAssistantMessage(List<AgentGraphMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            AgentGraphMessage candidate = messages.get(index);
            if (candidate.type() == AgentGraphMessageType.ASSISTANT && !candidate.hasToolCalls()) {
                return candidate;
            }
        }
        return null;
    }

    String appendSourceAppendix(String response, List<ExecutionSource> sources) {
        String normalized = response == null ? "" : response.trim();
        if (normalized.isBlank() || sources == null || sources.isEmpty() || hasSourceAppendix(normalized)) {
            return normalized;
        }
        StringBuilder builder = new StringBuilder(normalized)
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("## Sources")
                .append(System.lineSeparator());
        for (ExecutionSource source : deduplicateSources(sources)) {
            builder.append("- [")
                    .append(renderSourceLabel(source.kind()))
                    .append("] ")
                    .append(source.title());
            if (source.domain() != null && !source.domain().isBlank()) {
                builder.append(" | ").append(source.domain());
            }
            builder.append(" - ").append(source.url()).append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    MessageRecord persistMessage(String userId,
                                 String threadId,
                                 MessageRole role,
                                 String content,
                                 InteractionMode interactionMode,
                                 String runId,
                                 String taskId) {
        return messageRepository.append(userId, new MessageRecord(
                UUID.randomUUID().toString(),
                threadId,
                role,
                content,
                interactionMode,
                runId,
                taskId,
                Instant.now()
        ));
    }

    ConversationDegradationSummary collectDegradationSummary(List<AgentGraphMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ConversationDegradationSummary(false, List.of());
        }
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        for (AgentGraphMessage message : messages) {
            Map<String, Object> properties = message == null ? Map.of() : message.messageProperties();
            if (!Boolean.TRUE.equals(properties.get(DEGRADED_PROPERTY))) {
                continue;
            }
            Object reason = properties.get(DEGRADATION_REASON_PROPERTY);
            if (reason instanceof String value && !value.isBlank()) {
                reasons.add(value);
            } else {
                reasons.add("degraded");
            }
        }
        return new ConversationDegradationSummary(!reasons.isEmpty(), List.copyOf(reasons));
    }

    String renderPersistedUserContent(String content, List<ThreadFileReference> inputImages) {
        String trimmed = content == null ? "" : content.trim();
        if (inputImages == null || inputImages.isEmpty()) {
            return trimmed;
        }
        String imageSummary = inputImages.stream()
                .map(ThreadFileReference::name)
                .map(name -> name == null || name.isBlank() ? "image" : name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("image");
        if (trimmed.isBlank()) {
            return "[Attached images: " + imageSummary + "]";
        }
        return trimmed + "\n\n[Attached images: " + imageSummary + "]";
    }

    String workflowFor(ConversationRouteKind routeKind) {
        return switch (routeKind) {
            case DOCUMENT_QA -> "document-qa";
            case CHAT -> "chat";
            case RESEARCH_DRAFT -> "research-draft";
        };
    }

    private List<String> normalizeSkillIds(List<String> activeSkillIds) {
        if (activeSkillIds == null || activeSkillIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String activeSkillId : activeSkillIds) {
            if (activeSkillId == null || activeSkillId.isBlank()) {
                continue;
            }
            normalized.add(activeSkillId.trim());
        }
        return List.copyOf(normalized);
    }

    private String renderSourceLabel(String kind) {
        return switch (kind) {
            case "WEB_PAGE" -> "Web Page";
            case "WEATHER_REPORT" -> "Weather Data";
            default -> "Search Result";
        };
    }

    private boolean hasSourceAppendix(String response) {
        String normalized = response.toLowerCase();
        return normalized.contains("\n## sources") || normalized.contains("\n### sources");
    }
}
