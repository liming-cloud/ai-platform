package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.tenant.projection;

import java.time.Instant;

/** PostgreSQL 锁定读取的候选初始管理员主体与成员资格行。 */
public class HistoricalTenantMembershipRow {

    private long subjectId;
    private String loginName;
    private String passwordHash;
    private String subjectStatus;
    private long subjectVersion;
    private Instant subjectCreatedAt;
    private long membershipId;
    private String membershipRole;
    private String membershipStatus;
    private long permissionVersion;
    private long membershipVersion;
    private Instant membershipCreatedAt;

    public long getSubjectId() { return subjectId; }
    public void setSubjectId(long subjectId) { this.subjectId = subjectId; }
    public String getLoginName() { return loginName; }
    public void setLoginName(String loginName) { this.loginName = loginName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSubjectStatus() { return subjectStatus; }
    public void setSubjectStatus(String subjectStatus) { this.subjectStatus = subjectStatus; }
    public long getSubjectVersion() { return subjectVersion; }
    public void setSubjectVersion(long subjectVersion) { this.subjectVersion = subjectVersion; }
    public Instant getSubjectCreatedAt() { return subjectCreatedAt; }
    public void setSubjectCreatedAt(Instant subjectCreatedAt) { this.subjectCreatedAt = subjectCreatedAt; }
    public long getMembershipId() { return membershipId; }
    public void setMembershipId(long membershipId) { this.membershipId = membershipId; }
    public String getMembershipRole() { return membershipRole; }
    public void setMembershipRole(String membershipRole) { this.membershipRole = membershipRole; }
    public String getMembershipStatus() { return membershipStatus; }
    public void setMembershipStatus(String membershipStatus) { this.membershipStatus = membershipStatus; }
    public long getPermissionVersion() { return permissionVersion; }
    public void setPermissionVersion(long permissionVersion) { this.permissionVersion = permissionVersion; }
    public long getMembershipVersion() { return membershipVersion; }
    public void setMembershipVersion(long membershipVersion) { this.membershipVersion = membershipVersion; }
    public Instant getMembershipCreatedAt() { return membershipCreatedAt; }
    public void setMembershipCreatedAt(Instant membershipCreatedAt) { this.membershipCreatedAt = membershipCreatedAt; }
}
