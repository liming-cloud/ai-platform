package org.fujay.ai.platform.tenant.application.dto;

import java.util.List;

public record CurrentUserProfile(
        Long tenantId,
        String tenantCode,
        Long userId,
        String username,
        String displayName,
        List<RoleProfile> roles,
        List<PermissionProfile> permissions,
        List<MenuProfile> menus) {

    public record RoleProfile(Long roleId, String roleCode, String roleName) {
    }

    public record PermissionProfile(Long permissionId, String permissionCode, String permissionName) {
    }

    public record MenuProfile(
            Long menuId,
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String componentKey,
            String platformScope,
            Integer sortOrder) {
    }
}
