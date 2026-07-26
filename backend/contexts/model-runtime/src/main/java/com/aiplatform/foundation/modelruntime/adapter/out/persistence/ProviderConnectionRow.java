package com.aiplatform.foundation.modelruntime.adapter.out.persistence;

import java.time.Instant;

/**
 * 显式 SQL 查询使用的供应商连接行。
 */
public class ProviderConnectionRow {

    private String tenantId;
    private String id;
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
