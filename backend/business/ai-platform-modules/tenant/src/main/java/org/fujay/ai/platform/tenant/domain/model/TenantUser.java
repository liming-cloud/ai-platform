package org.fujay.ai.platform.tenant.domain.model;

import org.fujay.ai.platform.shared.ddd.Entity;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;

public record TenantUser(
        Long id,
        Long tenantId,
        String username,
        String displayName,
        String status,
        boolean systemAccount) implements Entity {

    public TenantUser {
        requireId(id, "用户标识不能为空");
        requireId(tenantId, "租户标识不能为空");
        TenantApplicationAccessSpec.requireNotBlank(username, "用户名不能为空");
        TenantApplicationAccessSpec.requireNotBlank(displayName, "用户名称不能为空");
        username = username.trim();
        displayName = displayName.trim();
        status = TenantApplicationAccessSpec.normalizeStatus(status);
    }

    public static TenantUser standard(Long id, Long tenantId, String username, String displayName, String status) {
        return new TenantUser(id, tenantId, username, displayName, status, false);
    }

    public static TenantUser systemAccount(Long id, Long tenantId, String username, String displayName, String status) {
        return new TenantUser(id, tenantId, username, displayName, status, true);
    }

    public boolean enabled() {
        return TenantApplicationAccessSpec.STATUS_ENABLED.equals(status);
    }

    public boolean grantsPlatformPermission(String permissionCode) {
        return systemAccount && permissionCode != null && !permissionCode.isBlank();
    }

    public void ensurePasswordChangeAllowed() {
    }

    public void ensureDeleteAllowed() {
        rejectSystemAccountMutation("系统内置管理员账号不可删除");
    }

    public void ensureDisableAllowed() {
        rejectSystemAccountMutation("系统内置管理员账号不可禁用");
    }

    public void ensureRoleMutationAllowed() {
        rejectSystemAccountMutation("系统内置管理员账号不可修改角色或权限");
    }

    public TenantUser rename(String newDisplayName) {
        rejectSystemAccountMutation("系统内置管理员账号只能修改密码");
        return new TenantUser(id, tenantId, username, newDisplayName, status, systemAccount);
    }

    public TenantUser changeStatus(String newStatus) {
        if (systemAccount && TenantApplicationAccessSpec.STATUS_DISABLED.equals(
                TenantApplicationAccessSpec.normalizeStatus(newStatus))) {
            throw new IllegalStateException("系统内置管理员账号不可禁用");
        }
        return new TenantUser(id, tenantId, username, displayName, newStatus, systemAccount);
    }

    private void rejectSystemAccountMutation(String message) {
        if (systemAccount) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
