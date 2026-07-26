package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

/**
 * MyBatis-Plus 表元数据实体，领域层不依赖该类型。
 */
@TableName("model_runtime.provider_connection")
public class ProviderConnectionEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID tenantId;
    private String providerKind;
    private String displayName;
    private String endpoint;
    private String region;
    private String authenticationType;
    private String credentialReference;
    private String status;
    private long version;
    private Boolean lastProbeSucceeded;
    private String lastProbeCode;
    private Instant lastProbedAt;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getProviderKind() {
        return providerKind;
    }

    public void setProviderKind(String providerKind) {
        this.providerKind = providerKind;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }

    public String getCredentialReference() {
        return credentialReference;
    }

    public void setCredentialReference(String credentialReference) {
        this.credentialReference = credentialReference;
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

    public Boolean getLastProbeSucceeded() {
        return lastProbeSucceeded;
    }

    public void setLastProbeSucceeded(Boolean lastProbeSucceeded) {
        this.lastProbeSucceeded = lastProbeSucceeded;
    }

    public String getLastProbeCode() {
        return lastProbeCode;
    }

    public void setLastProbeCode(String lastProbeCode) {
        this.lastProbeCode = lastProbeCode;
    }

    public Instant getLastProbedAt() {
        return lastProbedAt;
    }

    public void setLastProbedAt(Instant lastProbedAt) {
        this.lastProbedAt = lastProbedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
