package org.fujay.ai.platform.tenant.domain.model;

import org.fujay.ai.platform.shared.ddd.Entity;
import org.fujay.ai.platform.tenant.domain.specification.TenantApplicationAccessSpec;

public record IamPermission(Long id, Long tenantId, String permissionCode, String permissionName, String status)
        implements Entity {

    public IamPermission {
        requireId(id, "权限标识不能为空");
        requireId(tenantId, "租户标识不能为空");
        TenantApplicationAccessSpec.requireNotBlank(permissionCode, "权限编码不能为空");
        TenantApplicationAccessSpec.requireNotBlank(permissionName, "权限名称不能为空");
        permissionCode = permissionCode.trim();
        permissionName = permissionName.trim();
        status = TenantApplicationAccessSpec.normalizeStatus(status);
    }

    public IamPermission renameAndChangeStatus(String newPermissionName, String newStatus) {
        return new IamPermission(id, tenantId, permissionCode, newPermissionName, newStatus);
    }

    private static void requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
