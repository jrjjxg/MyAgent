package com.xg.platform.api.persistence.mybatisplus.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("thread_memory_snapshots")
public class ThreadMemorySnapshotEntity {

    @TableId("thread_id")
    private String threadId;
    private String userId;
    private String summary;
    private String lastCompactedMessageId;
    private String pendingHistoricalMessagesJson;
    private String recentEndMessageId;
    private Integer recentWindowSize;
    private String activeDraftId;
    private String activeTaskId;
    private String taskStage;
    private String activeSkillIdsJson;
    private Instant updatedAt;

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLastCompactedMessageId() {
        return lastCompactedMessageId;
    }

    public void setLastCompactedMessageId(String lastCompactedMessageId) {
        this.lastCompactedMessageId = lastCompactedMessageId;
    }

    public String getPendingHistoricalMessagesJson() {
        return pendingHistoricalMessagesJson;
    }

    public void setPendingHistoricalMessagesJson(String pendingHistoricalMessagesJson) {
        this.pendingHistoricalMessagesJson = pendingHistoricalMessagesJson;
    }

    public String getRecentEndMessageId() {
        return recentEndMessageId;
    }

    public void setRecentEndMessageId(String recentEndMessageId) {
        this.recentEndMessageId = recentEndMessageId;
    }

    public Integer getRecentWindowSize() {
        return recentWindowSize;
    }

    public void setRecentWindowSize(Integer recentWindowSize) {
        this.recentWindowSize = recentWindowSize;
    }

    public String getActiveDraftId() {
        return activeDraftId;
    }

    public void setActiveDraftId(String activeDraftId) {
        this.activeDraftId = activeDraftId;
    }

    public String getActiveTaskId() {
        return activeTaskId;
    }

    public void setActiveTaskId(String activeTaskId) {
        this.activeTaskId = activeTaskId;
    }

    public String getTaskStage() {
        return taskStage;
    }

    public void setTaskStage(String taskStage) {
        this.taskStage = taskStage;
    }

    public String getActiveSkillIdsJson() {
        return activeSkillIdsJson;
    }

    public void setActiveSkillIdsJson(String activeSkillIdsJson) {
        this.activeSkillIdsJson = activeSkillIdsJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
