package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection;

import java.time.Instant;

/** 平台租户管理员目录的 PostgreSQL 安全投影行。 */
public final class PlatformTenantAdministratorSummaryRow {

    private long membershipId;
    private String loginName;
    private String status;
    private long permissionVersion;
    private long version;
    private Instant createdAt;

    public long getMembershipId() { return membershipId; }
    public void setMembershipId(long value) { membershipId = value; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String value) { loginName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public long getPermissionVersion() { return permissionVersion; }
    public void setPermissionVersion(long value) { permissionVersion = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
