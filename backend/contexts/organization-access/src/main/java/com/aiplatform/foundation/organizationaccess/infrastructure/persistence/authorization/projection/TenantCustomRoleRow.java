package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.authorization.projection;

import java.time.Instant;

/** PostgreSQL 受锁自定义角色事实的基础设施读取投影。 */
public final class TenantCustomRoleRow {

    private long customRoleId;
    private long tenantId;
    private String roleName;
    private String status;
    private long version;
    private Instant createdAt;
    private Instant disabledAt;

    public long getCustomRoleId() {
        return customRoleId;
    }

    public void setCustomRoleId(long customRoleId) {
        this.customRoleId = customRoleId;
    }

    public long getTenantId() {
        return tenantId;
    }

    public void setTenantId(long tenantId) {
        this.tenantId = tenantId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public void setDisabledAt(Instant disabledAt) {
        this.disabledAt = disabledAt;
    }
}
