package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection;

import java.time.Instant;

/**
 * 生命周期最小读取投影的 PostgreSQL 行映射。
 */
public final class TenantLifecycleSummaryRow {

    private long tenantId;
    private String tenantCode;
    private String displayName;
    private String status;
    private long version;
    private Instant createdAt;
    private Instant lastChangedAt;
    private long administratorCount;

    public long getTenantId() { return tenantId; }
    public void setTenantId(long value) { tenantId = value; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String value) { tenantCode = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getLastChangedAt() { return lastChangedAt; }
    public void setLastChangedAt(Instant value) { lastChangedAt = value; }
    /** 只返回资格计数，不携带成员或主体明细。 */
    public long getAdministratorCount() { return administratorCount; }
    /** 保存数据库读取的资格计数，由应用投影拒绝非法负数。 */
    public void setAdministratorCount(long value) { administratorCount = value; }
}
