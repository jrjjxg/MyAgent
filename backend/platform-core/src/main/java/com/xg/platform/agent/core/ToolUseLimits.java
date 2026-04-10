package com.xg.platform.agent.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class ToolUseLimits implements Serializable {

    private final int maxToolCalls;
    private final int maxSearchCalls;
    private final int maxFetchCalls;
    private final int reflectionAfterSearches;
    private final int minVerifiedSources;
    private final long timeoutMs;
    private final AtomicInteger totalCalls = new AtomicInteger();
    private final AtomicInteger searchCalls = new AtomicInteger();
    private final AtomicInteger fetchCalls = new AtomicInteger();
    private final AtomicInteger reflectCalls = new AtomicInteger();
    private final AtomicInteger searchesSinceReflect = new AtomicInteger();

    public static ToolUseLimits fresh(int maxToolCalls,
                                      int maxSearchCalls,
                                      int maxFetchCalls,
                                      int reflectionAfterSearches,
                                      int minVerifiedSources,
                                      long timeoutMs) {
        return new ToolUseLimits(maxToolCalls, maxSearchCalls, maxFetchCalls, reflectionAfterSearches, minVerifiedSources, timeoutMs, 0, 0, 0, 0, 0);
    }

    @JsonCreator
    public ToolUseLimits(@JsonProperty("maxToolCalls") int maxToolCalls,
                         @JsonProperty("maxSearchCalls") int maxSearchCalls,
                         @JsonProperty("maxFetchCalls") int maxFetchCalls,
                         @JsonProperty("reflectionAfterSearches") int reflectionAfterSearches,
                         @JsonProperty("minVerifiedSources") int minVerifiedSources,
                         @JsonProperty("timeoutMs") long timeoutMs,
                         @JsonProperty("totalCalls") Integer totalCalls,
                         @JsonProperty("searchCalls") Integer searchCalls,
                         @JsonProperty("fetchCalls") Integer fetchCalls,
                         @JsonProperty("reflectCalls") Integer reflectCalls,
                         @JsonProperty("searchesSinceReflect") Integer searchesSinceReflect) {
        this.maxToolCalls = Math.max(1, maxToolCalls);
        this.maxSearchCalls = Math.max(0, maxSearchCalls);
        this.maxFetchCalls = Math.max(0, maxFetchCalls);
        this.reflectionAfterSearches = Math.max(0, reflectionAfterSearches);
        this.minVerifiedSources = Math.max(0, minVerifiedSources);
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.totalCalls.set(Math.max(0, totalCalls == null ? 0 : totalCalls));
        this.searchCalls.set(Math.max(0, searchCalls == null ? 0 : searchCalls));
        this.fetchCalls.set(Math.max(0, fetchCalls == null ? 0 : fetchCalls));
        this.reflectCalls.set(Math.max(0, reflectCalls == null ? 0 : reflectCalls));
        this.searchesSinceReflect.set(Math.max(0, searchesSinceReflect == null ? 0 : searchesSinceReflect));
    }

    public boolean tryAcquire(String toolName) {
        String normalized = toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
        if (totalCalls.incrementAndGet() > maxToolCalls) {
            totalCalls.decrementAndGet();
            return false;
        }
        if ("web_search".equals(normalized) && searchCalls.incrementAndGet() > maxSearchCalls) {
            searchCalls.decrementAndGet();
            totalCalls.decrementAndGet();
            return false;
        }
        if ("web_search".equals(normalized) && reflectionAfterSearches > 0) {
            if (searchesSinceReflect.incrementAndGet() > reflectionAfterSearches) {
                searchesSinceReflect.decrementAndGet();
                searchCalls.decrementAndGet();
                totalCalls.decrementAndGet();
                return false;
            }
        }
        if ("web_fetch".equals(normalized) && fetchCalls.incrementAndGet() > maxFetchCalls) {
            fetchCalls.decrementAndGet();
            totalCalls.decrementAndGet();
            return false;
        }
        if ("research_reflect".equals(normalized)) {
            reflectCalls.incrementAndGet();
            searchesSinceReflect.set(0);
        }
        return true;
    }

    @JsonProperty("maxToolCalls")
    public int maxToolCalls() {
        return maxToolCalls;
    }

    @JsonProperty("maxSearchCalls")
    public int maxSearchCalls() {
        return maxSearchCalls;
    }

    @JsonProperty("maxFetchCalls")
    public int maxFetchCalls() {
        return maxFetchCalls;
    }

    @JsonProperty("reflectionAfterSearches")
    public int reflectionAfterSearches() {
        return reflectionAfterSearches;
    }

    @JsonProperty("minVerifiedSources")
    public int minVerifiedSources() {
        return minVerifiedSources;
    }

    @JsonProperty("timeoutMs")
    public long timeoutMs() {
        return timeoutMs;
    }

    @JsonProperty("totalCalls")
    public int totalCalls() {
        return totalCalls.get();
    }

    @JsonProperty("searchCalls")
    public int searchCalls() {
        return searchCalls.get();
    }

    @JsonProperty("fetchCalls")
    public int fetchCalls() {
        return fetchCalls.get();
    }

    @JsonProperty("reflectCalls")
    public int reflectCalls() {
        return reflectCalls.get();
    }

    @JsonProperty("searchesSinceReflect")
    public int searchesSinceReflect() {
        return searchesSinceReflect.get();
    }
}
