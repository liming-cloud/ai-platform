package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.identity.entity;

import java.time.Instant;

/** 平台账户目录联表查询的基础设施行，仅用于构造受限读取投影。 */
public final class PlatformAccountSummaryRow {

    private long subjectId;
    private String loginName;
    private String status;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private String activeRoleCodes;

    public long getSubjectId() { return subjectId; }
    public void setSubjectId(long subjectId) { this.subjectId = subjectId; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getActiveRoleCodes() { return activeRoleCodes; }
    public void setActiveRoleCodes(String activeRoleCodes) { this.activeRoleCodes = activeRoleCodes; }
}
