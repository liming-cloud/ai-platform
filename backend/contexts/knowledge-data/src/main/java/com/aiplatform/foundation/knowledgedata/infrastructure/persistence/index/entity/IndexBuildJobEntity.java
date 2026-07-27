package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 索引构建作业持久化实体。
 */
@TableName("knowledge_data.index_build_job")
public class IndexBuildJobEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("knowledge_space_id")
    private Long knowledgeSpaceId;
    @TableField("index_version_id")
    private Long indexVersionId;
    @TableField("status")
    private String status;
    @TableField("attempt")
    private int attempt;
    @TableField("lease_id")
    private Long leaseId;
    @TableField("lease_until")
    private Instant leaseUntil;
    @TableField("failure_code")
    private String failureCode;
    @TableField("available_at")
    private Instant availableAt;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("completed_at")
    private Instant completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long value) { knowledgeSpaceId = value; }
    public Long getIndexVersionId() { return indexVersionId; }
    public void setIndexVersionId(Long value) { indexVersionId = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public Long getLeaseId() { return leaseId; }
    public void setLeaseId(Long leaseId) { this.leaseId = leaseId; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant value) { leaseUntil = value; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public Instant getAvailableAt() { return availableAt; }
    public void setAvailableAt(Instant value) { availableAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { completedAt = value; }
}
