package com.xg.platform.api.persistence.mybatisplus.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("research_drafts")
public class ResearchDraftEntity {

    @TableId("draft_id")
    private String draftId;
    private String userId;
    private String threadId;
    private String status;
    private String title;
    private String brief;
    private String objective;
    private String scope;
    private String outputFormat;
    private String constraintsJson;
    private String questionsJson;
    private Integer revision;
    private String planSummary;
    private String planStepsJson;
    private Boolean ready;
    private String lastUserMessageId;
    private String lastAssistantMessageId;
    private Instant createdAt;
    private Instant updatedAt;

    public String getDraftId() {
        return draftId;
    }

    public void setDraftId(String draftId) {
        this.draftId = draftId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBrief() {
        return brief;
    }

    public void setBrief(String brief) {
        this.brief = brief;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public String getConstraintsJson() {
        return constraintsJson;
    }

    public void setConstraintsJson(String constraintsJson) {
        this.constraintsJson = constraintsJson;
    }

    public String getQuestionsJson() {
        return questionsJson;
    }

    public void setQuestionsJson(String questionsJson) {
        this.questionsJson = questionsJson;
    }

    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    public String getPlanSummary() {
        return planSummary;
    }

    public void setPlanSummary(String planSummary) {
        this.planSummary = planSummary;
    }

    public String getPlanStepsJson() {
        return planStepsJson;
    }

    public void setPlanStepsJson(String planStepsJson) {
        this.planStepsJson = planStepsJson;
    }

    public Boolean getReady() {
        return ready;
    }

    public void setReady(Boolean ready) {
        this.ready = ready;
    }

    public String getLastUserMessageId() {
        return lastUserMessageId;
    }

    public void setLastUserMessageId(String lastUserMessageId) {
        this.lastUserMessageId = lastUserMessageId;
    }

    public String getLastAssistantMessageId() {
        return lastAssistantMessageId;
    }

    public void setLastAssistantMessageId(String lastAssistantMessageId) {
        this.lastAssistantMessageId = lastAssistantMessageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
