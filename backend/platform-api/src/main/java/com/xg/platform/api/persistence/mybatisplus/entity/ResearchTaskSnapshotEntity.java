package com.xg.platform.api.persistence.mybatisplus.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("research_task_snapshots")
public class ResearchTaskSnapshotEntity {

    @TableId("task_id")
    private String taskId;
    private String userId;
    private String threadId;
    private String phase;
    private Integer iterationNo;
    private String summary;
    private Boolean converged;
    private String payloadJson;
    private Instant updatedAt;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Integer getIterationNo() {
        return iterationNo;
    }

    public void setIterationNo(Integer iterationNo) {
        this.iterationNo = iterationNo;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Boolean getConverged() {
        return converged;
    }

    public void setConverged(Boolean converged) {
        this.converged = converged;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
