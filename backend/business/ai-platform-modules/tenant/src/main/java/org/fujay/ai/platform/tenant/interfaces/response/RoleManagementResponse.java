package org.fujay.ai.platform.tenant.interfaces.response;

import java.util.List;

public record RoleManagementResponse(
        List<RoleAccessResponse> roles, List<PermissionResponse> permissions, List<MenuResponse> menus) {

    public record RoleAccessResponse(
            String roleId,
            String roleCode,
            String roleName,
            String status,
            List<PermissionResponse> permissions,
            List<MenuResponse> menus) {
    }

    public record PermissionResponse(String permissionId, String permissionCode, String permissionName, String status) {
    }

    public record MenuResponse(
            String menuId,
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String componentKey,
            String platformScope,
            Integer sortOrder,
            String status) {
    }
}
