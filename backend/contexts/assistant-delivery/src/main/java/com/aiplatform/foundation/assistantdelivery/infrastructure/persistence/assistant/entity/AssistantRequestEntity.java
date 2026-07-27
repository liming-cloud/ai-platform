package com.aiplatform.foundation.assistantdelivery.infrastructure.persistence.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 助手写操作的幂等请求持久化实体。
 */
@TableName("assistant_delivery.assistant_request")
public final class AssistantRequestEntity {

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
    @TableField("assistant_id")
    private Long assistantId;
    @TableField("assistant_version_id")
    private Long assistantVersionId;
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

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getIdempotencyKeyHash() {
        return idempotencyKeyHash;
    }

    public void setIdempotencyKeyHash(String idempotencyKeyHash) {
        this.idempotencyKeyHash = idempotencyKeyHash;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public Long getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(Long assistantId) {
        this.assistantId = assistantId;
    }

    public Long getAssistantVersionId() {
        return assistantVersionId;
    }

    public void setAssistantVersionId(Long assistantVersionId) {
        this.assistantVersionId = assistantVersionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
