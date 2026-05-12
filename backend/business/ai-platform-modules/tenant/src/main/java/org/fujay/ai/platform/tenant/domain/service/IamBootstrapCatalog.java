package org.fujay.ai.platform.tenant.domain.service;

import java.util.List;
import org.fujay.ai.platform.tenant.domain.model.DefaultMenu;
import org.fujay.ai.platform.tenant.domain.model.DefaultPermission;

public final class IamBootstrapCatalog {

    private static final List<String> RETIRED_MENU_CODES = List.of("platform.rbac");
    private static final List<DefaultPermission> PERMISSIONS = List.of(
            new DefaultPermission("platform:tenant:read", "查看租户"),
            new DefaultPermission("platform:tenant:write", "维护租户"),
            new DefaultPermission("rbac:role:read", "查看角色"),
            new DefaultPermission("rbac:role:write", "维护角色"),
            new DefaultPermission("rbac:permission:read", "查看权限点"),
            new DefaultPermission("rbac:permission:write", "维护权限点"),
            new DefaultPermission("rbac:menu:read", "查看菜单"),
            new DefaultPermission("rbac:menu:write", "维护菜单"),
            new DefaultPermission("model:platform-catalog:read", "查看平台模型基础配置"),
            new DefaultPermission("model:platform-catalog:write", "维护平台模型基础配置"),
            new DefaultPermission("model:tenant-config:read", "查看租户模型配置"),
            new DefaultPermission("model:tenant-config:write", "维护租户模型配置"),
            new DefaultPermission("model:invocation:execute", "执行模型调用"));
    private static final List<DefaultMenu> MENUS = List.of(
            new DefaultMenu("platform.root", "平台管理", null, "/platform", "PlatformLayout", "PLATFORM", 10),
            new DefaultMenu("platform.tenants", "租户管理", "platform.root", "/platform/tenants", "PlatformTenants", "PLATFORM", 11),
            new DefaultMenu("platform.roles", "角色管理", "platform.root", "/platform/roles", "PlatformRoles", "PLATFORM", 12),
            new DefaultMenu("platform.permissions", "权限管理", "platform.root", "/platform/permissions", "PlatformPermissions", "PLATFORM", 13),
            new DefaultMenu("platform.menus", "菜单管理", "platform.root", "/platform/menus", "PlatformMenus", "PLATFORM", 14),
            new DefaultMenu("platform.model-catalog", "模型基础配置", "platform.root", "/platform/model-catalog", "PlatformModelCatalog", "PLATFORM", 15),
            new DefaultMenu("tenant.root", "租户平台", null, "/tenant", "TenantLayout", "TENANT", 20),
            new DefaultMenu("tenant.models", "模型配置", "tenant.root", "/tenant/models", "TenantModels", "TENANT", 21));

    private IamBootstrapCatalog() {
    }

    public static List<DefaultPermission> permissions() {
        return PERMISSIONS;
    }

    public static List<DefaultMenu> menus() {
        return MENUS;
    }

    public static List<String> retiredMenuCodes() {
        return RETIRED_MENU_CODES;
    }
}
