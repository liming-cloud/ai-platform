package com.aiplatform.foundation.knowledgedata.adapter.out.persistence;

import java.time.Instant;

/**
 * 索引版本数据库行。
 */
public final class IndexVersionRow {

    private String tenantId;
    private String knowledgeSpaceId;
    private String id;
    private String sourceSnapshotDigest;
    private String chunkPolicy;
    private String embeddingDeploymentId;
    private long embeddingDeploymentRevision;
    private int embeddingDimension;
    private String status;
    private long version;
    private String buildDigest;
    private int chunkCount;
    private String failureCode;
    private String createdBy;
    private Instant createdAt;
    private Instant readyAt;
    private Instant activatedAt;
    private Instant supersededAt;

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

    public String getSourceSnapshotDigest() {
        return sourceSnapshotDigest;
    }

    public void setSourceSnapshotDigest(String sourceSnapshotDigest) {
        this.sourceSnapshotDigest = sourceSnapshotDigest;
    }

    public String getChunkPolicy() {
        return chunkPolicy;
    }

    public void setChunkPolicy(String chunkPolicy) {
        this.chunkPolicy = chunkPolicy;
    }

    public String getEmbeddingDeploymentId() {
        return embeddingDeploymentId;
    }

    public void setEmbeddingDeploymentId(String embeddingDeploymentId) {
        this.embeddingDeploymentId = embeddingDeploymentId;
    }

    public long getEmbeddingDeploymentRevision() {
        return embeddingDeploymentRevision;
    }

    public void setEmbeddingDeploymentRevision(long value) {
        embeddingDeploymentRevision = value;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
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

    public String getBuildDigest() {
        return buildDigest;
    }

    public void setBuildDigest(String buildDigest) {
        this.buildDigest = buildDigest;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
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

    public Instant getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(Instant readyAt) {
        this.readyAt = readyAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public void setSupersededAt(Instant supersededAt) {
        this.supersededAt = supersededAt;
    }
}
