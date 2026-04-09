package com.xg.platform.api.persistence.mybatisplus.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("user_model_provider_config")
public class UserModelProviderConfigEntity {

    @TableId("config_id")
    private String configId;
    private String userId;
    private String providerId;
    private Boolean enabled;
    private byte[] apiKeyCiphertext;
    private byte[] apiKeyIv;
    private String modelOverride;
    private String baseUrlOverride;
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

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public byte[] getApiKeyCiphertext() {
        return apiKeyCiphertext;
    }

    public void setApiKeyCiphertext(byte[] apiKeyCiphertext) {
        this.apiKeyCiphertext = apiKeyCiphertext;
    }

    public byte[] getApiKeyIv() {
        return apiKeyIv;
    }

    public void setApiKeyIv(byte[] apiKeyIv) {
        this.apiKeyIv = apiKeyIv;
    }

    public String getModelOverride() {
        return modelOverride;
    }

    public void setModelOverride(String modelOverride) {
        this.modelOverride = modelOverride;
    }

    public String getBaseUrlOverride() {
        return baseUrlOverride;
    }

    public void setBaseUrlOverride(String baseUrlOverride) {
        this.baseUrlOverride = baseUrlOverride;
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
