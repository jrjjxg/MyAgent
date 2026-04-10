package com.xg.platform.agent.core.application;

import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;
import com.xg.platform.contracts.memory.MemoryExtractionJobStatus;
import com.xg.platform.contracts.conversation.InteractionMode;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.MessageRole;
import com.xg.platform.memory.port.LongTermMemoryExtractionRequest;
import com.xg.platform.memory.port.LongTermMemoryJobDispatcher;
import com.xg.platform.memory.port.LongTermMemoryJobRepository;
import com.xg.platform.conversation.port.MessageRepository;

import java.util.List;
import java.util.Optional;

public class LongTermMemoryJobScheduler {

    private final LongTermMemoryJobRepository longTermMemoryJobRepository;
    private final LongTermMemoryJobDispatcher longTermMemoryJobDispatcher;
    private final MessageRepository messageRepository;
    private final String extractorVersion;
    private final int turnInterval;

    public LongTermMemoryJobScheduler(LongTermMemoryJobRepository longTermMemoryJobRepository,
                                      LongTermMemoryJobDispatcher longTermMemoryJobDispatcher,
                                      MessageRepository messageRepository,
                                      String extractorVersion,
                                      int turnInterval) {
        this.longTermMemoryJobRepository = longTermMemoryJobRepository;
        this.longTermMemoryJobDispatcher = longTermMemoryJobDispatcher;
        this.messageRepository = messageRepository;
        this.extractorVersion = extractorVersion;
        this.turnInterval = Math.max(1, turnInterval);
    }

    public void schedule(String userId, String threadId, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        Optional<MessageRecord> currentMessage = messageRepository.findById(userId, threadId, messageId);
        if (currentMessage.isEmpty()
                || currentMessage.get().role() != MessageRole.ASSISTANT
                || currentMessage.get().interactionMode() != InteractionMode.CHAT) {
            return;
        }
        if (longTermMemoryJobRepository.hasPendingJob(userId, threadId, extractorVersion)) {
            return;
        }
        List<MessageRecord> messages = messageRepository.listMessages(userId, threadId);
        String lastSucceededMessageId = longTermMemoryJobRepository.findLatestSucceeded(userId, threadId, extractorVersion)
                .filter(job -> job.status() == MemoryExtractionJobStatus.SUCCEEDED)
                .map(MemoryExtractionJobRecord::messageId)
                .orElse(null);
        int eligibleTurnCount = countEligibleTurns(messages, lastSucceededMessageId, messageId);
        if (eligibleTurnCount < turnInterval) {
            return;
        }
        longTermMemoryJobRepository.createQueuedIfAbsent(userId, threadId, messageId, extractorVersion, eligibleTurnCount)
                .ifPresent(this::dispatch);
    }

    private int countEligibleTurns(List<MessageRecord> messages, String lastSucceededMessageId, String currentMessageId) {
        int count = 0;
        boolean started = lastSucceededMessageId == null
                || lastSucceededMessageId.isBlank()
                || messages.stream().noneMatch(message -> message.messageId().equals(lastSucceededMessageId));
        for (MessageRecord message : messages) {
            if (!started) {
                if (message.messageId().equals(lastSucceededMessageId)) {
                    started = true;
                }
                continue;
            }
            if (message.role() == MessageRole.ASSISTANT && message.interactionMode() == InteractionMode.CHAT) {
                count++;
            }
            if (message.messageId().equals(currentMessageId)) {
                break;
            }
        }
        return count;
    }

    private void dispatch(MemoryExtractionJobRecord jobRecord) {
        longTermMemoryJobDispatcher.dispatch(new LongTermMemoryExtractionRequest(
                jobRecord.jobId(),
                jobRecord.userId(),
                jobRecord.threadId(),
                jobRecord.messageId(),
                jobRecord.extractorVersion()
        ));
    }
}
