package com.aiplatform.foundation.knowledgedata.adapter.out.persistence.datasource;

import java.time.Instant;

/**
 * 文本数据源持久化行。
 */
public final class TextDataSourceRow {

    private String tenantId;
    private String knowledgeSpaceId;
    private String id;
    private String name;
    private String status;
    private long version;
    private int contentVersion;
    private int publishedContentVersion;
    private String createdBy;
    private Instant createdAt;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    public void setKnowledgeSpaceId(String knowledgeSpaceId) {
        this.knowledgeSpaceId = knowledgeSpaceId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
