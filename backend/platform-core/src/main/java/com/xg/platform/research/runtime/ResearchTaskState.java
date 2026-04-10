package com.xg.platform.research.runtime;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ResearchTaskState extends AgentState {

    public static final String USER_ID = "userId";
    public static final String THREAD_ID = "threadId";
    public static final String TASK_ID = "taskId";
    public static final String PROVIDER_ID = "providerId";
    public static final String TASK_INPUT = "taskInput";
    public static final String SKIP_EXECUTION = "skipExecution";
    public static final String RESEARCH_BRIEF = "researchBrief";
    public static final String MEMORY_VIEW = "memoryView";
    public static final String SESSION_SUMMARY = "sessionSummary";
    public static final String LONG_TERM_MEMORY = "longTermMemory";
    public static final String APPROVED_PLAN = "approvedPlan";
    public static final String RESEARCH_PLAN = "researchPlan";
    public static final String RESEARCH_SESSION = "researchSession";
    public static final String CURRENT_STAGE = "currentStage";
    public static final String CURRENT_STEP_INDEX = "currentStepIndex";
    public static final String UNIT_RESULTS = "unitResults";
    public static final String FINDINGS = "findings";
    public static final String FINAL_REPORT = "finalReport";
    public static final String REPORT_BLOCKS = "reportBlocks";
    public static final String ITERATIONS = "iterations";
    public static final String SOURCES = "sources";
    public static final String CITATIONS = "citations";
    public static final String PLAN = "plan";
    public static final String RESULT_ARTIFACT_ID = "resultArtifactId";
    public static final String PLAN_ARTIFACT_ID = "planArtifactId";
    public static final String COMPLETION_EVENTS_PUBLISHED = "completionEventsPublished";
    public static final String RESULT = "result";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            UNIT_RESULTS, Channels.base(ArrayList::new),
            FINDINGS, Channels.base(ArrayList::new),
            REPORT_BLOCKS, Channels.base(ArrayList::new),
            ITERATIONS, Channels.base(ArrayList::new),
            SOURCES, Channels.base(ArrayList::new),
            CITATIONS, Channels.base(ArrayList::new),
            PLAN, Channels.base(ArrayList::new)
    );

    public ResearchTaskState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> userId() {
        return value(USER_ID);
    }

    public Optional<String> threadId() {
        return value(THREAD_ID);
    }

    public Optional<String> taskId() {
        return value(TASK_ID);
    }

    public Optional<String> providerId() {
        return value(PROVIDER_ID);
    }

    public Optional<String> taskInput() {
        return value(TASK_INPUT);
    }

    public Optional<Boolean> skipExecution() {
        return value(SKIP_EXECUTION);
    }

    public Optional<String> researchBrief() {
        return value(RESEARCH_BRIEF);
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
    public <T> Optional<T> approvedPlan() {
        return (Optional<T>) value(APPROVED_PLAN);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> researchPlan() {
        return (Optional<T>) value(RESEARCH_PLAN);
    }

    public Optional<String> currentStage() {
        return value(CURRENT_STAGE);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> researchSession() {
        return (Optional<T>) value(RESEARCH_SESSION);
    }

    public Optional<Integer> currentStepIndex() {
        return value(CURRENT_STEP_INDEX);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> unitResults() {
        return (Optional<T>) value(UNIT_RESULTS);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> findings() {
        return (Optional<T>) value(FINDINGS);
    }

    public Optional<String> finalReport() {
        return value(FINAL_REPORT);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> reportBlocks() {
        return (List<T>) value(REPORT_BLOCKS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> iterations() {
        return (List<T>) value(ITERATIONS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> sources() {
        return (List<T>) value(SOURCES).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> citations() {
        return (List<T>) value(CITATIONS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> plan() {
        return (List<T>) value(PLAN).orElse(List.of());
    }

    public Optional<String> resultArtifactId() {
        return value(RESULT_ARTIFACT_ID);
    }

    public Optional<String> planArtifactId() {
        return value(PLAN_ARTIFACT_ID);
    }

    public Optional<Boolean> completionEventsPublished() {
        return value(COMPLETION_EVENTS_PUBLISHED);
    }
}
