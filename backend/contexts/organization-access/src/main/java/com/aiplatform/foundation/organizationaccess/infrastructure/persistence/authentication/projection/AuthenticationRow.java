package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authentication.projection;

/**
 * 认证查询返回的最小数据库投影。
 */
public class AuthenticationRow {

    private Long tenantId;
    private Long subjectId;
    private Long membershipId;
    private Long permissionVersion;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(Long membershipId) {
        this.membershipId = membershipId;
    }

    public Long getPermissionVersion() {
        return permissionVersion;
    }

    public void setPermissionVersion(Long permissionVersion) {
        this.permissionVersion = permissionVersion;
    }
}
