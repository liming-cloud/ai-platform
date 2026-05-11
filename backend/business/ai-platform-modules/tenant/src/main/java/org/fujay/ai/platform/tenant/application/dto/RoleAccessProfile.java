package org.fujay.ai.platform.tenant.application.dto;

import java.util.List;

public record RoleAccessProfile(
        Long roleId,
        String roleCode,
        String roleName,
        String status,
        List<PermissionProfile> permissions,
        List<MenuProfile> menus) {

    public record PermissionProfile(Long permissionId, String permissionCode, String permissionName, String status) {
    }

    public record MenuProfile(
            Long menuId,
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
