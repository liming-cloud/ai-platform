package com.aiplatform.foundation.knowledgedata.infrastructure.persistence.index.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 索引版本幂等请求持久化实体。
 */
@TableName("knowledge_data.index_version_request")
public class IndexVersionRequestEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("operation")
    private String operation;
    @TableField("idempotency_key_hash")
    private String idempotencyKeyHash;
    @TableField("request_fingerprint")
    private String requestFingerprint;
    @TableField("knowledge_space_id")
    private Long knowledgeSpaceId;
    @TableField("index_version_id")
    private Long indexVersionId;
    @TableField("result_status")
    private String resultStatus;
    @TableField("result_version")
    private long resultVersion;
    @TableField("created_at")
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    public void setIdempotencyKeyHash(String value) { idempotencyKeyHash = value; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { requestFingerprint = value; }
    public Long getKnowledgeSpaceId() { return knowledgeSpaceId; }
    public void setKnowledgeSpaceId(Long value) { knowledgeSpaceId = value; }
    public Long getIndexVersionId() { return indexVersionId; }
    public void setIndexVersionId(Long value) { indexVersionId = value; }
    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String value) { resultStatus = value; }
    public long getResultVersion() { return resultVersion; }
    public void setResultVersion(long value) { resultVersion = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
