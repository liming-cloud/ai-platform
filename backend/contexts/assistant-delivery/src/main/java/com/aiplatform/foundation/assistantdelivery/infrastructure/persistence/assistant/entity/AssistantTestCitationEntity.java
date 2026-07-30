package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 草稿测试终态的最小引用标识持久化实体。
 */
@TableName("assistant_delivery.assistant_test_run_citation")
public final class AssistantTestCitationEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id") private Long tenantId;
    @TableField("assistant_test_run_id") private Long assistantTestRunId;
    @TableField("sequence") private int sequence;
    @TableField("data_source_id") private Long dataSourceId;
    @TableField("content_version") private int contentVersion;
    @TableField("chunk_id") private String chunkId;
    @TableField("ordinal") private int ordinal;

    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public Long getAssistantTestRunId() { return assistantTestRunId; }
    public void setAssistantTestRunId(Long value) { assistantTestRunId = value; }
    public int getSequence() { return sequence; }
    public void setSequence(int value) { sequence = value; }
    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long value) { dataSourceId = value; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int value) { contentVersion = value; }
    public String getChunkId() { return chunkId; }
    public void setChunkId(String value) { chunkId = value; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int value) { ordinal = value; }
}
