package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

/** 平台专表的最小普通 CRUD 实体；声明数组转换由独立 SQL 投影负责。 */
@com.baomidou.mybatisplus.annotation.TableName("model_runtime.platform_model_deployment_request")
public class PlatformModelDeploymentRequestEntity {
    @com.baomidou.mybatisplus.annotation.TableId(value = "idempotency_key_hash", type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    private String idempotencyKeyHash;
    @com.baomidou.mybatisplus.annotation.TableField("request_fingerprint")
    private String requestFingerprint;
    @com.baomidou.mybatisplus.annotation.TableField("platform_model_deployment_id")
    private Long platformModelDeploymentId;

    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    public void setIdempotencyKeyHash(String value) { this.idempotencyKeyHash = value; }

    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { this.requestFingerprint = value; }

    public Long getPlatformModelDeploymentId() { return platformModelDeploymentId; }
    public void setPlatformModelDeploymentId(Long value) { this.platformModelDeploymentId = value; }
}
