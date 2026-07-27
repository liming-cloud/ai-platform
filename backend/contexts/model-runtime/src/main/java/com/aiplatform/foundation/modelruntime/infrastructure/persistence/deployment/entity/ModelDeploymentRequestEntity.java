package com.aiplatform.foundation.modelruntime.infrastructure.persistence.deployment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 模型部署幂等请求持久化实体。
 */
@TableName("model_runtime.model_deployment_request")
public class ModelDeploymentRequestEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("idempotency_key_hash")
    private String idempotencyKeyHash;
    @TableField("request_fingerprint")
    private String requestFingerprint;
    @TableField("model_deployment_id")
    private Long modelDeploymentId;
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

    public Long getModelDeploymentId() {
        return modelDeploymentId;
    }

    public void setModelDeploymentId(Long modelDeploymentId) {
        this.modelDeploymentId = modelDeploymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
