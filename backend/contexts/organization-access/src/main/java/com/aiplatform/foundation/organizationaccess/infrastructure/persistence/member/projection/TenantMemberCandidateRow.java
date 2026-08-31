package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.projection;

/** 租户成员候选账户的最小安全 PostgreSQL 行投影。 */
public final class TenantMemberCandidateRow {

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
