package com.xg.platform.research.runtime;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Optional;

public class ResearchScopingState extends AgentState {

    public static final String USER_ID = "userId";
    public static final String THREAD_ID = "threadId";
    public static final String REQUEST = "request";
    public static final String RUN_CONTEXT_KEY = "runContextKey";
    public static final String MEMORY_VIEW = "memoryView";
    public static final String SESSION_SUMMARY = "sessionSummary";
    public static final String LONG_TERM_MEMORY = "longTermMemory";
    public static final String CURRENT_DRAFT = "currentDraft";
    public static final String RUN_ID = "runId";
    public static final String USER_MESSAGE = "userMessage";
    public static final String SCOPING_RESPONSE = "scopingResponse";
    public static final String ASSISTANT_CONTENT = "assistantContent";
    public static final String DRAFT_RECORD = "draftRecord";
    public static final String ASSISTANT_MESSAGE = "assistantMessage";
    public static final String EVENTS_PUBLISHED = "eventsPublished";
    public static final String RESULT = "result";

    public static final Map<String, org.bsc.langgraph4j.state.Channel<?>> SCHEMA = Map.of();

    public ResearchScopingState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userId() {
        return value(USER_ID);
    }

    public Optional<String> threadId() {
        return value(THREAD_ID);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> request() {
        return (Optional<T>) value(REQUEST);
    }

    public Optional<String> runContextKey() {
        return value(RUN_CONTEXT_KEY);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> memoryView() {
        return (Optional<T>) value(MEMORY_VIEW);
    }

    public Optional<String> sessionSummary() {
        return value(SESSION_SUMMARY);
    }

    public Optional<String> longTermMemory() {
        return value(LONG_TERM_MEMORY);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> currentDraft() {
        return (Optional<T>) value(CURRENT_DRAFT);
    }

    public Optional<String> runId() {
        return value(RUN_ID);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> userMessage() {
        return (Optional<T>) value(USER_MESSAGE);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> scopingResponse() {
        return (Optional<T>) value(SCOPING_RESPONSE);
    }

    public Optional<String> assistantContent() {
        return value(ASSISTANT_CONTENT);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> draftRecord() {
        return (Optional<T>) value(DRAFT_RECORD);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> assistantMessage() {
        return (Optional<T>) value(ASSISTANT_MESSAGE);
    }

    public Optional<Boolean> eventsPublished() {
        return value(EVENTS_PUBLISHED);
    }
}
