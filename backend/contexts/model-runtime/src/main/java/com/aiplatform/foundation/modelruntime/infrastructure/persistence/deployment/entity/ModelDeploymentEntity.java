package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * MyBatis-Plus 模型部署表元数据实体，领域层不依赖该类型。
 */
@TableName("model_runtime.model_deployment")
public class ModelDeploymentEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("provider_connection_id")
    private Long providerConnectionId;
    @TableField("provider_connection_revision")
    private long providerConnectionRevision;
    @TableField("model_code")
    private String modelCode;
    @TableField("display_name")
    private String displayName;
    @TableField("model_type")
    private String modelType;
    @TableField("capabilities")
    private String[] capabilities;
    @TableField("context_window")
    private int contextWindow;
    @TableField("output_limit")
    private Integer outputLimit;
    @TableField("embedding_dimension")
    private Integer embeddingDimension;
    @TableField("status")
    private String status;
    @Version
    @TableField("version")
    private long version;
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

    public Long getProviderConnectionId() {
        return providerConnectionId;
    }

    public void setProviderConnectionId(Long providerConnectionId) {
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
