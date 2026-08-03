package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection;

import java.time.Instant;

/** PostgreSQL 锁定读取的历史租户行。 */
public class HistoricalTenantLifecycleRow {

    private long tenantId;
    private String tenantCode;
    private String displayName;
    private String status;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
