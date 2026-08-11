package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection;

/** 平台角色绑定认证查询返回的最小数据库投影。 */
public class PlatformRoleAuthorizationRow {

    private Long subjectId;
    private Long permissionVersion;
    private String roleCode;

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public Long getPermissionVersion() { return permissionVersion; }
    public void setPermissionVersion(Long permissionVersion) { this.permissionVersion = permissionVersion; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
}
