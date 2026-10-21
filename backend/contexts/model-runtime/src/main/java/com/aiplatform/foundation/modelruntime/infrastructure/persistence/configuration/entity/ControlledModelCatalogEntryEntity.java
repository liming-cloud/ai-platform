package com.aiplatform.foundation.modelruntime.infrastructure.persistence.configuration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 受控模型目录表的 MyBatis-Plus 元数据实体。
 */
@TableName("model_runtime.controlled_model_catalog_entry")
public class ControlledModelCatalogEntryEntity {

    @TableId(value = "id", type = IdType.INPUT) private Long id;
    @TableField("provider_kind") private String providerKind;
    private String endpoint;
    @TableField("model_code") private String modelCode;
    @TableField("model_type") private String modelType;
    @TableField("context_window") private Integer contextWindow;
    @TableField("output_limit") private Integer outputLimit;
    @TableField("embedding_dimension") private Integer embeddingDimension;
    @TableField("rerank_candidate_limit") private Integer rerankCandidateLimit;
    @TableField("source_platform_deployment_id") private Long sourcePlatformDeploymentId;
    @TableField("source_platform_deployment_revision") private Long sourcePlatformDeploymentRevision;
    private String status;
    @Version private long version;
    @TableField("created_at") private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProviderKind() { return providerKind; }
    public void setProviderKind(String providerKind) { this.providerKind = providerKind; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public Integer getContextWindow() { return contextWindow; }
    public void setContextWindow(Integer contextWindow) { this.contextWindow = contextWindow; }
    public Integer getOutputLimit() { return outputLimit; }
    public void setOutputLimit(Integer outputLimit) { this.outputLimit = outputLimit; }
    public Integer getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(Integer embeddingDimension) { this.embeddingDimension = embeddingDimension; }
    public Integer getRerankCandidateLimit() { return rerankCandidateLimit; }
    public void setRerankCandidateLimit(Integer value) { this.rerankCandidateLimit = value; }
    public Long getSourcePlatformDeploymentId() { return sourcePlatformDeploymentId; }
    public void setSourcePlatformDeploymentId(Long sourcePlatformDeploymentId) { this.sourcePlatformDeploymentId = sourcePlatformDeploymentId; }
    public Long getSourcePlatformDeploymentRevision() { return sourcePlatformDeploymentRevision; }
    public void setSourcePlatformDeploymentRevision(Long sourcePlatformDeploymentRevision) { this.sourcePlatformDeploymentRevision = sourcePlatformDeploymentRevision; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
