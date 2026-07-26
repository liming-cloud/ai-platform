package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import java.time.Instant;

/**
 * 显式 SQL 查询使用的模型部署行。
 */
public class ModelDeploymentRow {

    private String tenantId;
    private String id;
    private String providerConnectionId;
    private long providerConnectionRevision;
    private String modelCode;
    private String displayName;
    private String modelType;
    private String capabilities;
    private int contextWindow;
    private Integer outputLimit;
    private Integer embeddingDimension;
    private String status;
    private long version;
    private Instant createdAt;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProviderConnectionId() {
        return providerConnectionId;
    }

    public void setProviderConnectionId(String providerConnectionId) {
        this.providerConnectionId = providerConnectionId;
    }

    public long getProviderConnectionRevision() {
        return providerConnectionRevision;
    }

    public void setProviderConnectionRevision(long providerConnectionRevision) {
        this.providerConnectionRevision = providerConnectionRevision;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(int contextWindow) {
        this.contextWindow = contextWindow;
    }

    public Integer getOutputLimit() {
        return outputLimit;
    }

    public void setOutputLimit(Integer outputLimit) {
        this.outputLimit = outputLimit;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(Integer embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
