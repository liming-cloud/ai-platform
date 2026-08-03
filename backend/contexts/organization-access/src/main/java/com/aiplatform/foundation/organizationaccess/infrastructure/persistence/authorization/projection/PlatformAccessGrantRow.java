package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection;

/**
 * 平台授权认证查询返回的最小数据库投影。
 */
public class PlatformAccessGrantRow {

    private Long subjectId;
    private Long permissionVersion;
    private String permissionCode;

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public Long getPermissionVersion() {
        return permissionVersion;
    }

    public void setPermissionVersion(Long permissionVersion) {
        this.permissionVersion = permissionVersion;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }
}
