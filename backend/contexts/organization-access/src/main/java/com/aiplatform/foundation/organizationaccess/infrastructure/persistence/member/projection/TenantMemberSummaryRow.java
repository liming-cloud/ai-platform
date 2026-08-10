package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection;

import java.time.Instant;

/** 成员目录最小安全投影的 PostgreSQL 行映射。 */
public final class TenantMemberSummaryRow {

    private long membershipId;
    private String loginName;
    private String membershipRole;
    private String status;
    private long permissionVersion;
    private String builtInRoleCodes;
    private Instant createdAt;

    public long getMembershipId() { return membershipId; }
    public void setMembershipId(long value) { membershipId = value; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String value) { loginName = value; }
    public String getMembershipRole() { return membershipRole; }
    public void setMembershipRole(String value) { membershipRole = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public long getPermissionVersion() { return permissionVersion; }
    public void setPermissionVersion(long value) { permissionVersion = value; }
    public String getBuiltInRoleCodes() { return builtInRoleCodes; }
    public void setBuiltInRoleCodes(String value) { builtInRoleCodes = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
