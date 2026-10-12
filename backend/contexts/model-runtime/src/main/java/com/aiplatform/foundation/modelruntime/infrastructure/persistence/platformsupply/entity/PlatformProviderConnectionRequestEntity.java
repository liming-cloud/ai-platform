package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 幂等键的非秘密请求绑定；不保存凭据或凭据摘要。 */
@TableName("model_runtime.platform_provider_connection_request")
public class PlatformProviderConnectionRequestEntity {
    @TableId(type = IdType.INPUT)
    private String idempotencyKeyHash;
    private String requestFingerprint;
    private Long platformProviderConnectionId;
    public String getIdempotencyKeyHash() { return idempotencyKeyHash; }
    public void setIdempotencyKeyHash(String value) { this.idempotencyKeyHash = value; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String value) { this.requestFingerprint = value; }
    public Long getPlatformProviderConnectionId() { return platformProviderConnectionId; }
    public void setPlatformProviderConnectionId(Long value) { this.platformProviderConnectionId = value; }
}
