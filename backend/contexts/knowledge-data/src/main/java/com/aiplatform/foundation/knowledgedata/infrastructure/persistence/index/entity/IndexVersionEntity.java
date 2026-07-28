package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;

/**
 * 索引版本持久化实体。
 */
@TableName("knowledge_data.index_version")
public class IndexVersionEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_space_id")
    private Long knowledgeSpaceId;
    @TableField("source_snapshot_digest")
    private String sourceSnapshotDigest;
    @TableField("chunk_policy")
    private String chunkPolicy;
    @TableField("embedding_deployment_id")
    private Long embeddingDeploymentId;
    @TableField("embedding_deployment_revision")
    private Long embeddingDeploymentRevision;
    @TableField("embedding_configuration_id")
    private Long embeddingConfigurationId;
    @TableField("embedding_configuration_revision")
    private Long embeddingConfigurationRevision;
    @TableField("embedding_catalog_entry_id")
    private Long embeddingCatalogEntryId;
    @TableField("embedding_catalog_entry_revision")
    private Long embeddingCatalogEntryRevision;
    @TableField("embedding_dimension")
    private int embeddingDimension;
    @TableField("status")
    private String status;
    @Version
    @TableField("version")
    private long version;
    @TableField("build_digest")
    private String buildDigest;
    @TableField("chunk_count")
    private int chunkCount;
    @TableField("failure_code")
    private String failureCode;
    @TableField("created_by")
    private Long createdBy;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("ready_at")
    private Instant readyAt;
    @TableField("activated_at")
    private Instant activatedAt;
    @TableField("superseded_at")
    private Instant supersededAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long value) { knowledgeSpaceId = value; }
    public String getSourceSnapshotDigest() { return sourceSnapshotDigest; }
    public void setSourceSnapshotDigest(String value) { sourceSnapshotDigest = value; }
    public String getChunkPolicy() { return chunkPolicy; }
    public void setChunkPolicy(String chunkPolicy) { this.chunkPolicy = chunkPolicy; }
    public Long getEmbeddingDeploymentId() { return embeddingDeploymentId; }
    public void setEmbeddingDeploymentId(Long value) { embeddingDeploymentId = value; }
    public Long getEmbeddingDeploymentRevision() { return embeddingDeploymentRevision; }
    public void setEmbeddingDeploymentRevision(Long value) { embeddingDeploymentRevision = value; }
    public Long getEmbeddingConfigurationId() { return embeddingConfigurationId; }
    public void setEmbeddingConfigurationId(Long value) { embeddingConfigurationId = value; }
    public Long getEmbeddingConfigurationRevision() { return embeddingConfigurationRevision; }
    public void setEmbeddingConfigurationRevision(Long value) { embeddingConfigurationRevision = value; }
    public Long getEmbeddingCatalogEntryId() { return embeddingCatalogEntryId; }
    public void setEmbeddingCatalogEntryId(Long value) { embeddingCatalogEntryId = value; }
    public Long getEmbeddingCatalogEntryRevision() { return embeddingCatalogEntryRevision; }
    public void setEmbeddingCatalogEntryRevision(Long value) { embeddingCatalogEntryRevision = value; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int value) { embeddingDimension = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getBuildDigest() { return buildDigest; }
    public void setBuildDigest(String buildDigest) { this.buildDigest = buildDigest; }
    public int getChunkCount() { return chunkCount; }
    public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public Instant getSupersededAt() { return supersededAt; }
    public void setSupersededAt(Instant value) { supersededAt = value; }
}
