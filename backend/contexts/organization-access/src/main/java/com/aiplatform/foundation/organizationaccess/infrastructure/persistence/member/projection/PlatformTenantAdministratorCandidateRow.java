package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection;

/** 平台租户管理员候选的 PostgreSQL 安全投影行。 */
public final class PlatformTenantAdministratorCandidateRow {

    private long subjectId;
    private String loginName;
    private String status;

    public long getSubjectId() { return subjectId; }
    public void setSubjectId(long value) { subjectId = value; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String value) { loginName = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
}
