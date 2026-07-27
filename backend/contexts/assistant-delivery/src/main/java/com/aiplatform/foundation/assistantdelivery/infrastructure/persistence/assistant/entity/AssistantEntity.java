package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 助手身份持久化实体。
 */
@TableName("assistant_delivery.assistant")
public final class AssistantEntity {

    @TableField("tenant_id")
    private Long tenantId;
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("display_name")
    private String displayName;
    @TableField("latest_version_number")
    private int latestVersionNumber;
    @Version
    @TableField("version")
    private long version;
    @TableField("created_by")
    private Long createdBy;
    @TableField("created_at")
    private Instant createdAt;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long value) {
        tenantId = value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        id = value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String value) {
        displayName = value;
    }

    public int getLatestVersionNumber() {
        return latestVersionNumber;
    }

    public void setLatestVersionNumber(int value) {
        latestVersionNumber = value;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        version = value;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long value) {
        createdBy = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        createdAt = value;
    }
}
