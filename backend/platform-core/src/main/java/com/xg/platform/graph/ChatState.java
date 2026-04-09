package com.xg.platform.graph;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Optional;

public class ChatState extends AgentState {

    public static final String USER_ID = "userId";
    public static final String THREAD_ID = "threadId";
    public static final String REQUEST = "request";
    public static final String RUN_CONTEXT_KEY = "runContextKey";
    public static final String MEMORY_VIEW = "memoryView";
    public static final String SESSION_SUMMARY = "sessionSummary";
    public static final String LONG_TERM_MEMORY = "longTermMemory";
    public static final String RESULT = "result";

    public static final Map<String, org.bsc.langgraph4j.state.Channel<?>> SCHEMA = Map.of();

    public ChatState(Map<String, Object> initData) {
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
}
