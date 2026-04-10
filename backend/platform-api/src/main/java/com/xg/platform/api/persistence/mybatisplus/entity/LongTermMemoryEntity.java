package com.xg.platform.api.persistence.mybatisplus.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xg.platform.api.persistence.mybatisplus.typehandler.JsonbStringTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.time.Instant;

@TableName(value = "long_term_memory", autoResultMap = true)
public class LongTermMemoryEntity {

    @TableId("memory_id")
    private String memoryId;
    private String userId;
    private String memoryType;
    private String canonicalKey;
    private String title;
    private String content;
    @TableField(value = "value_json", jdbcType = JdbcType.OTHER, typeHandler = JsonbStringTypeHandler.class)
    private String valueJson;
    private String sourceThreadId;
    private String sourceMessageId;
    private String sourceTaskId;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getCanonicalKey() {
        return canonicalKey;
    }

    public void setCanonicalKey(String canonicalKey) {
        this.canonicalKey = canonicalKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getValueJson() {
        return valueJson;
    }

    public void setValueJson(String valueJson) {
        this.valueJson = valueJson;
    }

    public String getSourceThreadId() {
        return sourceThreadId;
    }

    public void setSourceThreadId(String sourceThreadId) {
        this.sourceThreadId = sourceThreadId;
    }

    public String getSourceMessageId() {
        return sourceMessageId;
    }

    public void setSourceMessageId(String sourceMessageId) {
        this.sourceMessageId = sourceMessageId;
    }

    public String getSourceTaskId() {
        return sourceTaskId;
    }

    public void setSourceTaskId(String sourceTaskId) {
        this.sourceTaskId = sourceTaskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
