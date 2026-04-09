package com.xg.platform.graph;

import com.xg.platform.agent.core.AgentGraphMessage;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InteractionState extends AgentState {

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
    public static final String ROUTE_KIND = "routeKind";
    public static final String WORKFLOW = "workflow";
    public static final String TOOLS_ENABLED = "toolsEnabled";
    public static final String PROVIDER_ID = "providerId";
    public static final String PROMPT = "prompt";
    public static final String MESSAGES = "messages";
    public static final String CURRENT_USER_GRAPH_MESSAGE_ID = "currentUserGraphMessageId";
    public static final String ACTIVE_SKILL_IDS = "activeSkillIds";
    public static final String ARTIFACTS = "artifacts";
    public static final String UPLOADED_FILES = "uploadedFiles";
    public static final String INPUT_IMAGES = "inputImages";
    public static final String AVAILABLE_TOOLS = "availableTools";
    public static final String AVAILABLE_SKILLS = "availableSkills";
    public static final String AVAILABLE_DOCUMENTS = "availableDocuments";
    public static final String RETRIEVED_CHUNKS = "retrievedChunks";
    public static final String TOOL_USE_LIMITS = "toolUseLimits";
    public static final String SOURCES = "sources";
    public static final String ACTIONS = "actions";
    public static final String DOCUMENT_READING_PLAN = "documentReadingPlan";
    public static final String DOCUMENT_WORKING_MEMORY = "documentWorkingMemory";
    public static final String DOCUMENT_PHASE = "documentPhase";
    public static final String DOCUMENT_SEARCH_HINTS = "documentSearchHints";
    public static final String DOCUMENT_SCRATCHPAD = "documentScratchpad";
    public static final String ASSISTANT_CONTENT = "assistantContent";
    public static final String DRAFT_RECORD = "draftRecord";
    public static final String ASSISTANT_MESSAGE = "assistantMessage";
    public static final String EVENTS_PUBLISHED = "eventsPublished";
    public static final String RESULT = "result";

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(MESSAGES, Channels.appender(ArrayList::new)),
            Map.entry(ACTIVE_SKILL_IDS, Channels.base(ArrayList::new)),
            Map.entry(ARTIFACTS, Channels.base(ArrayList::new)),
            Map.entry(UPLOADED_FILES, Channels.base(ArrayList::new)),
            Map.entry(INPUT_IMAGES, Channels.base(ArrayList::new)),
            Map.entry(AVAILABLE_TOOLS, Channels.base(ArrayList::new)),
            Map.entry(AVAILABLE_SKILLS, Channels.base(ArrayList::new)),
            Map.entry(AVAILABLE_DOCUMENTS, Channels.base(ArrayList::new)),
            Map.entry(RETRIEVED_CHUNKS, Channels.base(ArrayList::new)),
            Map.entry(SOURCES, Channels.base(ArrayList::new)),
            Map.entry(ACTIONS, Channels.base(ArrayList::new)),
            Map.entry(DOCUMENT_SEARCH_HINTS, Channels.base(ArrayList::new))
    );

    public InteractionState(Map<String, Object> initData) {
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
    public <T> Optional<T> routeKind() {
        return (Optional<T>) value(ROUTE_KIND);
    }

    public Optional<String> workflow() {
        return value(WORKFLOW);
    }

    public Optional<Boolean> toolsEnabled() {
        return value(TOOLS_ENABLED);
    }

    public Optional<String> providerId() {
        return value(PROVIDER_ID);
    }

    public Optional<String> prompt() {
        return value(PROMPT);
    }

    @SuppressWarnings("unchecked")
    public List<AgentGraphMessage> messages() {
        return (List<AgentGraphMessage>) value(MESSAGES).orElse(List.of());
    }

    public Optional<String> currentUserGraphMessageId() {
        return value(CURRENT_USER_GRAPH_MESSAGE_ID);
    }

    @SuppressWarnings("unchecked")
    public List<String> activeSkillIds() {
        return (List<String>) value(ACTIVE_SKILL_IDS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> artifacts() {
        return (List<T>) value(ARTIFACTS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> uploadedFiles() {
        return (List<T>) value(UPLOADED_FILES).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> inputImages() {
        return (List<T>) value(INPUT_IMAGES).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> availableTools() {
        return (List<T>) value(AVAILABLE_TOOLS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> availableSkills() {
        return (List<T>) value(AVAILABLE_SKILLS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> availableDocuments() {
        return (List<T>) value(AVAILABLE_DOCUMENTS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> retrievedChunks() {
        return (List<T>) value(RETRIEVED_CHUNKS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> toolUseLimits() {
        return (Optional<T>) value(TOOL_USE_LIMITS);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> sources() {
        return (List<T>) value(SOURCES).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> actions() {
        return (List<String>) value(ACTIONS).orElse(List.of());
    }

    public Optional<String> assistantContent() {
        return value(ASSISTANT_CONTENT);
    }

    public Optional<String> documentReadingPlan() {
        return value(DOCUMENT_READING_PLAN);
    }

    public Optional<String> documentWorkingMemory() {
        return value(DOCUMENT_WORKING_MEMORY);
    }

    public Optional<String> documentPhase() {
        return value(DOCUMENT_PHASE);
    }

    @SuppressWarnings("unchecked")
    public List<String> documentSearchHints() {
        return (List<String>) value(DOCUMENT_SEARCH_HINTS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> documentScratchpad() {
        return (Optional<T>) value(DOCUMENT_SCRATCHPAD);
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
