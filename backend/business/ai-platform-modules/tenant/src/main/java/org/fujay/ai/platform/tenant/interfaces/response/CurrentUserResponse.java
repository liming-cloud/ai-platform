package org.fujay.ai.platform.tenant.interfaces.response;

import java.util.List;

public record CurrentUserResponse(
        String tenantId,
        String tenantCode,
        String userId,
        String username,
        String displayName,
        List<RoleResponse> roles,
        List<PermissionResponse> permissions,
        List<MenuResponse> menus) {

    public record RoleResponse(String roleId, String roleCode, String roleName) {
    }

    public record PermissionResponse(String permissionId, String permissionCode, String permissionName) {
    }

    public record MenuResponse(
            String menuId,
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String componentKey,
            String platformScope,
            Integer sortOrder) {
    }
}
