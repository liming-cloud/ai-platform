package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

/**
 * MyBatis-Plus 模型部署表元数据实体，领域层不依赖该类型。
 */
@TableName("model_runtime.model_deployment")
public class ModelDeploymentEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID tenantId;
    private UUID providerConnectionId;
    private long providerConnectionRevision;
    private String modelCode;
    private String displayName;
    private String modelType;
    private String[] capabilities;
    private int contextWindow;
    private Integer outputLimit;
    private Integer embeddingDimension;
    private String status;
    private long version;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getProviderConnectionId() {
        return providerConnectionId;
    }

    public void setProviderConnectionId(UUID providerConnectionId) {
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

    public String[] getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String[] capabilities) {
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
