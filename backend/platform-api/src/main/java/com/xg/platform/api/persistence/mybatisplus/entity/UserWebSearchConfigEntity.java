package com.xg.platform.api.persistence.mybatisplus.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("user_web_search_config")
public class UserWebSearchConfigEntity {

    @TableId("config_id")
    private String configId;
    private String userId;
    private String providerOverride;
    private byte[] tavilyApiKeyCiphertext;
    private byte[] tavilyApiKeyIv;
    private String searchApiBaseUrlOverride;
    private Instant createdAt;
    private Instant updatedAt;

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProviderOverride() {
        return providerOverride;
    }

    public void setProviderOverride(String providerOverride) {
        this.providerOverride = providerOverride;
    }

    public byte[] getTavilyApiKeyCiphertext() {
        return tavilyApiKeyCiphertext;
    }

    public void setTavilyApiKeyCiphertext(byte[] tavilyApiKeyCiphertext) {
        this.tavilyApiKeyCiphertext = tavilyApiKeyCiphertext;
    }

    public byte[] getTavilyApiKeyIv() {
        return tavilyApiKeyIv;
    }

    public void setTavilyApiKeyIv(byte[] tavilyApiKeyIv) {
        this.tavilyApiKeyIv = tavilyApiKeyIv;
    }

    public String getSearchApiBaseUrlOverride() {
        return searchApiBaseUrlOverride;
    }

    public void setSearchApiBaseUrlOverride(String searchApiBaseUrlOverride) {
        this.searchApiBaseUrlOverride = searchApiBaseUrlOverride;
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
