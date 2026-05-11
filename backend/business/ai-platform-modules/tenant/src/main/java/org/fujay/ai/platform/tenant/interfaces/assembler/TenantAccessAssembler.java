package org.fujay.ai.platform.tenant.interfaces.assembler;

import org.fujay.ai.platform.tenant.application.dto.AuthenticatedSession;
import org.fujay.ai.platform.tenant.application.dto.CurrentUserProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleManagementProfile;
import org.fujay.ai.platform.tenant.application.dto.TenantProfile;
import org.fujay.ai.platform.tenant.interfaces.response.CurrentUserResponse;
import org.fujay.ai.platform.tenant.interfaces.response.CurrentUserResponse.MenuResponse;
import org.fujay.ai.platform.tenant.interfaces.response.CurrentUserResponse.PermissionResponse;
import org.fujay.ai.platform.tenant.interfaces.response.CurrentUserResponse.RoleResponse;
import org.fujay.ai.platform.tenant.interfaces.response.LoginResponse;
import org.fujay.ai.platform.tenant.interfaces.response.RoleManagementResponse;
import org.fujay.ai.platform.tenant.interfaces.response.TenantResponse;
import org.springframework.stereotype.Component;

@Component
public class TenantAccessAssembler {

    public LoginResponse toLoginResponse(AuthenticatedSession session) {
        return new LoginResponse(
                session.tokenName(),
                session.tokenValue(),
                String.valueOf(session.tenantId()),
                session.tenantCode(),
                String.valueOf(session.userId()),
                session.username(),
                session.displayName());
    }

    public CurrentUserResponse toCurrentUserResponse(CurrentUserProfile profile) {
        return new CurrentUserResponse(
                String.valueOf(profile.tenantId()),
                profile.tenantCode(),
                String.valueOf(profile.userId()),
                profile.username(),
                profile.displayName(),
                profile.roles().stream()
                        .map(role -> new RoleResponse(String.valueOf(role.roleId()), role.roleCode(), role.roleName()))
                        .toList(),
                profile.permissions().stream()
                        .map(permission -> new PermissionResponse(
                                String.valueOf(permission.permissionId()),
                                permission.permissionCode(),
                                permission.permissionName()))
                        .toList(),
                profile.menus().stream()
                        .map(menu -> new MenuResponse(
                                String.valueOf(menu.menuId()),
                                menu.menuCode(),
                                menu.menuName(),
                                menu.parentCode(),
                                menu.routePath(),
                                menu.componentKey(),
                                menu.platformScope(),
                                menu.sortOrder()))
                        .toList());
    }

    public TenantResponse toTenantResponse(TenantProfile profile) {
        return new TenantResponse(
                String.valueOf(profile.tenantId()), profile.tenantCode(), profile.tenantName(), profile.status());
    }

    public RoleManagementResponse toRoleManagementResponse(RoleManagementProfile profile) {
        return new RoleManagementResponse(
                profile.roles().stream().map(this::toRoleAccessResponse).toList(),
                profile.permissions().stream()
                        .map(permission -> new RoleManagementResponse.PermissionResponse(
                                String.valueOf(permission.permissionId()),
                                permission.permissionCode(),
                                permission.permissionName(),
                                permission.status()))
                        .toList(),
                profile.menus().stream().map(this::toRoleMenuResponse).toList());
    }

    public RoleManagementResponse.RoleAccessResponse toRoleAccessResponse(RoleAccessProfile profile) {
        return new RoleManagementResponse.RoleAccessResponse(
                String.valueOf(profile.roleId()),
                profile.roleCode(),
                profile.roleName(),
                profile.status(),
                profile.permissions().stream()
                        .map(permission -> new RoleManagementResponse.PermissionResponse(
                                String.valueOf(permission.permissionId()),
                                permission.permissionCode(),
                                permission.permissionName(),
                                permission.status()))
                        .toList(),
                profile.menus().stream().map(this::toRoleMenuResponse).toList());
    }

    public RoleManagementResponse.PermissionResponse toPermissionResponse(RoleAccessProfile.PermissionProfile profile) {
        return new RoleManagementResponse.PermissionResponse(
                String.valueOf(profile.permissionId()), profile.permissionCode(), profile.permissionName(), profile.status());
    }

    public RoleManagementResponse.MenuResponse toMenuResponse(RoleAccessProfile.MenuProfile profile) {
        return toRoleMenuResponse(profile);
    }

    private RoleManagementResponse.MenuResponse toRoleMenuResponse(RoleAccessProfile.MenuProfile menu) {
        return new RoleManagementResponse.MenuResponse(
                String.valueOf(menu.menuId()),
                menu.menuCode(),
                menu.menuName(),
                menu.parentCode(),
                menu.routePath(),
                menu.componentKey(),
                menu.platformScope(),
                menu.sortOrder(),
                menu.status());
    }
}
