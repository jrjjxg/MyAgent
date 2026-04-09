package com.xg.platform.api.persistence.mybatisplus.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("user_skill_config")
public class SkillUserConfigEntity {

    @TableId("config_id")
    private String configId;
    private String userId;
    private String skillId;
    private Boolean enabled;
    private byte[] envCiphertext;
    private byte[] envIv;
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

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public byte[] getEnvCiphertext() {
        return envCiphertext;
    }

    public void setEnvCiphertext(byte[] envCiphertext) {
        this.envCiphertext = envCiphertext;
    }

    public byte[] getEnvIv() {
        return envIv;
    }

    public void setEnvIv(byte[] envIv) {
        this.envIv = envIv;
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
