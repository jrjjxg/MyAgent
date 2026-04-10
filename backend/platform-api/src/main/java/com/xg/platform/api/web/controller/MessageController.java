package com.xg.platform.api.web.controller;

import com.xg.platform.api.config.CurrentUserId;
import com.xg.platform.conversation.application.ConversationCommandService;
import com.xg.platform.contracts.conversation.MessageRecord;
import com.xg.platform.contracts.conversation.PostMessageRequest;
import com.xg.platform.contracts.shared.event.RunEvent;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

@RestController
@RequestMapping("/threads/{threadId}/messages")
public class MessageController {

    private static final Logger logger = Logger.getLogger(MessageController.class.getName());

    private final ConversationCommandService conversationCommandService;
    private final ExecutorService agentExecutionExecutor;

    public MessageController(ConversationCommandService conversationCommandService,
                             ExecutorService agentExecutionExecutor) {
        this.conversationCommandService = conversationCommandService;
        this.agentExecutionExecutor = agentExecutionExecutor;
    }

    @GetMapping
    public List<MessageRecord> listMessages(@CurrentUserId String userId,
                                            @PathVariable String threadId) {
        return conversationCommandService.listMessages(userId, threadId);
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter postMessage(@CurrentUserId String userId,
                                  @PathVariable String threadId,
                                  @RequestBody PostMessageRequest request,
                                  HttpServletResponse response) {
        conversationCommandService.prepareMessageExecution(userId, threadId, request);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean streamClosed = new AtomicBoolean(false);
        emitter.onCompletion(() -> streamClosed.set(true));
        emitter.onTimeout(() -> streamClosed.set(true));
        emitter.onError(error -> streamClosed.set(true));
        agentExecutionExecutor.execute(() -> {
            try {
                conversationCommandService.executeMessage(userId, threadId, request, event -> sendEvent(emitter, streamClosed, event));
            } catch (RuntimeException exception) {
                if (!streamClosed.get()) {
                    logger.warning(() -> "Streaming run failed for thread " + threadId + ": " + exception.getMessage());
                }
            } finally {
                completeEmitter(emitter, streamClosed);
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, AtomicBoolean streamClosed, RunEvent event) {
        if (streamClosed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event.eventType()).data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            streamClosed.set(true);
            logger.fine(() -> "Stopping SSE stream after client disconnect: " + exception.getMessage());
        }
    }

    private void completeEmitter(SseEmitter emitter, AtomicBoolean streamClosed) {
        if (!streamClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
        }
    }
}
