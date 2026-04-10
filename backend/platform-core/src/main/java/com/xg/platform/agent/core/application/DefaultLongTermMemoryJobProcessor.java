package com.xg.platform.agent.core.application;

import com.xg.platform.contracts.memory.MemoryExtractionJobRecord;
import com.xg.platform.memory.port.LongTermMemoryExtractionRequest;
import com.xg.platform.memory.port.LongTermMemoryJobProcessor;
import com.xg.platform.memory.port.LongTermMemoryJobRepository;

public class DefaultLongTermMemoryJobProcessor implements LongTermMemoryJobProcessor {

    private final LongTermMemoryJobRepository longTermMemoryJobRepository;
    private final LongTermMemoryExtractionService longTermMemoryExtractionService;
    private final int maxAttempts;

    public DefaultLongTermMemoryJobProcessor(LongTermMemoryJobRepository longTermMemoryJobRepository,
                                             LongTermMemoryExtractionService longTermMemoryExtractionService,
                                             int maxAttempts) {
        this.longTermMemoryJobRepository = longTermMemoryJobRepository;
        this.longTermMemoryExtractionService = longTermMemoryExtractionService;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public void process(LongTermMemoryExtractionRequest request) {
        if (request == null || request.jobId() == null || request.jobId().isBlank()) {
            return;
        }
        MemoryExtractionJobRecord running = longTermMemoryJobRepository.markRunning(request.jobId()).orElse(null);
        if (running == null) {
            return;
        }
        try {
            longTermMemoryExtractionService.extractFromCompletedMessage(
                    request.userId(),
                    request.threadId(),
                    request.messageId()
            );
            longTermMemoryJobRepository.markSucceeded(request.jobId());
        } catch (RuntimeException exception) {
            boolean terminal = running.attemptCount() + 1 >= maxAttempts;
            longTermMemoryJobRepository.markFailure(request.jobId(), safeMessage(exception), terminal);
            if (!terminal) {
                throw exception;
            }
        }
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
