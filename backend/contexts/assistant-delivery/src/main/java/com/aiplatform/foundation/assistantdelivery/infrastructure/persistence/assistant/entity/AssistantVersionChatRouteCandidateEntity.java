package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 助手版本冻结聊天候选的持久化行；候选顺序由 `ordinal` 固化。
 */
@TableName("assistant_delivery.assistant_version_chat_route_candidate")
public final class AssistantVersionChatRouteCandidateEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id") private Long tenantId;
    @TableField("assistant_version_id") private Long assistantVersionId;
    @TableField("ordinal") private int ordinal;
    @TableField("configuration_id") private Long configurationId;
    @TableField("configuration_revision") private long configurationRevision;
    @TableField("catalog_entry_id") private Long catalogEntryId;
    @TableField("catalog_entry_revision") private long catalogEntryRevision;
    @TableField("output_limit") private int outputLimit;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(Long value) { assistantVersionId = value; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int value) { ordinal = value; }
    public Long getConfigurationId() { return configurationId; }
    public void setConfigurationId(Long value) { configurationId = value; }
    public long getConfigurationRevision() { return configurationRevision; }
    public void setConfigurationRevision(long value) { configurationRevision = value; }
    public Long getCatalogEntryId() { return catalogEntryId; }
    public void setCatalogEntryId(Long value) { catalogEntryId = value; }
    public long getCatalogEntryRevision() { return catalogEntryRevision; }
    public void setCatalogEntryRevision(long value) { catalogEntryRevision = value; }
    public int getOutputLimit() { return outputLimit; }
    public void setOutputLimit(int value) { outputLimit = value; }
}
