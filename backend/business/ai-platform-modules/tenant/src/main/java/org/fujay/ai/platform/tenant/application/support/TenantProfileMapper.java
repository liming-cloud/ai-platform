package org.fujay.ai.platform.tenant.application.support;

import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile.MenuProfile;
import org.fujay.ai.platform.tenant.application.dto.RoleAccessProfile.PermissionProfile;
import org.fujay.ai.platform.tenant.application.dto.TenantProfile;
import org.fujay.ai.platform.tenant.domain.aggregate.Tenant;
import org.fujay.ai.platform.tenant.domain.model.IamMenu;
import org.fujay.ai.platform.tenant.domain.model.IamPermission;
import org.fujay.ai.platform.tenant.domain.model.IamRole;
import org.fujay.ai.platform.tenant.domain.model.RoleAccess;

public final class TenantProfileMapper {

    private TenantProfileMapper() {
    }

    public static TenantProfile toTenantProfile(Tenant tenant) {
        return new TenantProfile(tenant.id(), tenant.tenantCode(), tenant.tenantName(), tenant.status());
    }

    public static RoleAccessProfile toRoleAccessProfile(RoleAccess roleAccess) {
        IamRole role = roleAccess.role();
        return new RoleAccessProfile(
                role.id(),
                role.roleCode(),
                role.roleName(),
                role.status(),
                roleAccess.permissions().stream().map(TenantProfileMapper::toPermissionProfile).toList(),
                roleAccess.menus().stream().map(TenantProfileMapper::toMenuProfile).toList());
    }

    public static PermissionProfile toPermissionProfile(IamPermission permission) {
        return new PermissionProfile(
                permission.id(), permission.permissionCode(), permission.permissionName(), permission.status());
    }

    public static MenuProfile toMenuProfile(IamMenu menu) {
        return new MenuProfile(
                menu.id(),
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
