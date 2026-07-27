package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 文本数据源持久化实体。
 */
@TableName("knowledge_data.text_data_source")
public class TextDataSourceEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_space_id")
    private Long knowledgeSpaceId;
    @TableField("name")
    private String name;
    @TableField("status")
    private String status;
    @Version
    @TableField("version")
    private long version;
    @TableField("content_version")
    private int contentVersion;
    @TableField("published_content_version")
    private int publishedContentVersion;
    @TableField("created_by")
    private Long createdBy;
    @TableField("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public void setKnowledgeSpaceId(Long knowledgeSpaceId) {
        this.knowledgeSpaceId = knowledgeSpaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public int getContentVersion() {
        return contentVersion;
    }

    public void setContentVersion(int contentVersion) {
        this.contentVersion = contentVersion;
    }

    public int getPublishedContentVersion() {
        return publishedContentVersion;
    }

    public void setPublishedContentVersion(int publishedContentVersion) {
        this.publishedContentVersion = publishedContentVersion;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
