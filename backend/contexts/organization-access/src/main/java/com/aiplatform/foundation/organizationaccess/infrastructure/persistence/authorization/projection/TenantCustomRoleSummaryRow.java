package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection;

/** 自定义角色目录一行受限 PostgreSQL 投影。 */
public class TenantCustomRoleSummaryRow {

    private long customRoleId;
    private String roleName;
    private String permissionCodes;
    private long version;
    private String status;
    private long boundMemberCount;

    public long getCustomRoleId() { return customRoleId; }
    public void setCustomRoleId(long customRoleId) { this.customRoleId = customRoleId; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getPermissionCodes() { return permissionCodes; }
    public void setPermissionCodes(String permissionCodes) { this.permissionCodes = permissionCodes; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getBoundMemberCount() { return boundMemberCount; }
    public void setBoundMemberCount(long boundMemberCount) { this.boundMemberCount = boundMemberCount; }
}
