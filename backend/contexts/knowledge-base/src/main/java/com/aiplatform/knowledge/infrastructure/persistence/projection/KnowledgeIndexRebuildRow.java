package com.aiplatform.knowledge.infrastructure.persistence.projection;

import java.time.Instant;

public class KnowledgeIndexRebuildRow {
    private Long id;
    private Long tenantId;
    private Long knowledgeBaseId;
    private Long generation;
    private Long targetModelConfigId;
    private String targetEmbeddingModel;
    private String indexVersion;
    private String status;
    private Instant membersFrozenAt;
    private Instant createdAt;
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public Long getGeneration() { return generation; }
    public void setGeneration(Long generation) { this.generation = generation; }
    public Long getTargetModelConfigId() { return targetModelConfigId; }
    public void setTargetModelConfigId(Long targetModelConfigId) { this.targetModelConfigId = targetModelConfigId; }
    public String getTargetEmbeddingModel() { return targetEmbeddingModel; }
    public void setTargetEmbeddingModel(String targetEmbeddingModel) { this.targetEmbeddingModel = targetEmbeddingModel; }
    public String getIndexVersion() { return indexVersion; }
    public void setIndexVersion(String indexVersion) { this.indexVersion = indexVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getMembersFrozenAt() { return membersFrozenAt; }
    public void setMembersFrozenAt(Instant membersFrozenAt) { this.membersFrozenAt = membersFrozenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
