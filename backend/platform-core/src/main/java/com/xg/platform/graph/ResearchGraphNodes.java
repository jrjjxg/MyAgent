package com.xg.platform.graph;

import java.util.Map;

public interface ResearchGraphNodes {

    Map<String, Object> hydrateTask(ResearchTaskState state);

    Map<String, Object> normalizePlan(ResearchTaskState state);

    Map<String, Object> initializeSession(ResearchTaskState state);

    Map<String, Object> planAgenda(ResearchTaskState state);

    Map<String, Object> discoverySearch(ResearchTaskState state);

    Map<String, Object> intermediateSynthesis(ResearchTaskState state);

    Map<String, Object> gapAnalysis(ResearchTaskState state);

    Map<String, Object> routeIteration(ResearchTaskState state);

    Map<String, Object> focusedFollowup(ResearchTaskState state);

    Map<String, Object> convergeFinalize(ResearchTaskState state);

    Map<String, Object> writeArtifacts(ResearchTaskState state);

    Map<String, Object> markTaskCompleted(ResearchTaskState state);

    Map<String, Object> publishCompletionEvents(ResearchTaskState state);
}
