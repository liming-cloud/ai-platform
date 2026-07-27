package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 助手写操作的幂等请求持久化实体。
 */
@TableName("assistant_delivery.assistant_request")
public final class AssistantRequestEntity {

    @TableField("tenant_id")
    private Long tenantId;
    @TableField("operation")
    private String operation;
    @TableField("idempotency_key_hash")
    private String idempotencyKeyHash;
    @TableField("request_fingerprint")
    private String requestFingerprint;
    @TableField("assistant_id")
    private Long assistantId;
    @TableField("assistant_version_id")
    private Long assistantVersionId;
    @TableField("created_at")
    private Instant createdAt;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long value) { tenantId = value; }
    public String getOperation() { return operation; }
    public void setOperation(String value) { operation = value; }
    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    public void setIdempotencyKeyHash(String value) { idempotencyKeyHash = value; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { requestFingerprint = value; }
    public Long getAssistantId() { return assistantId; }
    public void setAssistantId(Long value) { assistantId = value; }
    public Long getAssistantVersionId() { return assistantVersionId; }
    public void setAssistantVersionId(Long value) { assistantVersionId = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
