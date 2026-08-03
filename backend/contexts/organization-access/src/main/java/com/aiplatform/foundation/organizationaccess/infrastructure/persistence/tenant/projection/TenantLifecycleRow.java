package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection;

import java.time.Instant;

/**
 * 租户生命周期聚合的受锁持久化读取投影。
 */
public class TenantLifecycleRow {

    private long tenantId;
    private String tenantCode;
    private String displayName;
    private long initialAdministratorSubjectId;
    private String status;
    private long version;
    private Instant createdAt;

    public long getTenantId() {
        return tenantId;
    }

    public void setTenantId(long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public long getInitialAdministratorSubjectId() {
        return initialAdministratorSubjectId;
    }

    public void setInitialAdministratorSubjectId(long initialAdministratorSubjectId) {
        this.initialAdministratorSubjectId = initialAdministratorSubjectId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
