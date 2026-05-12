package org.fujay.ai.platform.tenant.domain.model;

import org.fujay.ai.platform.shared.ddd.Entity;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;

public record IamRole(Long id, Long tenantId, String roleCode, String roleName, String status) implements Entity {

    public static final String PLATFORM_ADMIN_ROLE_CODE = "PLATFORM_ADMIN";

    public IamRole {
        requireId(id, "角色标识不能为空");
        requireId(tenantId, "租户标识不能为空");
        TenantApplicationAccessSpec.requireNotBlank(roleCode, "角色编码不能为空");
        TenantApplicationAccessSpec.requireNotBlank(roleName, "角色名称不能为空");
        roleCode = roleCode.trim();
        roleName = roleName.trim();
        status = TenantApplicationAccessSpec.normalizeStatus(status);
    }

    public IamRole renameAndChangeStatus(String newRoleName, String newStatus) {
        ensureMutableRoleDefinition();
        return new IamRole(id, tenantId, roleCode, newRoleName, newStatus);
    }

    public boolean platformAdminRole() {
        return PLATFORM_ADMIN_ROLE_CODE.equals(roleCode);
    }

    public void ensureRoleAccessMutationAllowed() {
        if (platformAdminRole()) {
            throw new IllegalStateException("平台管理员角色由系统内置管理员账号天然拥有，不允许修改授权关系");
        }
    }

    private void ensureMutableRoleDefinition() {
        if (platformAdminRole()) {
            throw new IllegalStateException("平台管理员角色不可修改");
        }
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
