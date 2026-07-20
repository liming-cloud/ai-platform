package com.aiplatform.identity.presentation.dto;

/**
 * 控制台菜单响应项，作为登录态权限快照的一部分返回给前端路由层。
 */
public record ConsoleMenuItem(
        String code,
        String name,
        String platform,
        String path,
        String routeName,
        String componentKey,
        String permission,
        int sort
) {
    public ConsoleMenuItem {
        code = IdentityResponseContracts.requireText(code, "菜单编码");
        name = IdentityResponseContracts.requireText(name, "菜单名称");
        platform = IdentityResponseContracts.requireText(platform, "菜单平台");
        path = IdentityResponseContracts.requireText(path, "菜单路径");
        routeName = IdentityResponseContracts.requireText(routeName, "路由名称");
        componentKey = IdentityResponseContracts.requireText(componentKey, "组件键");
        permission = IdentityResponseContracts.requireText(permission, "菜单权限");
        if (sort < 0) {
            throw new IllegalArgumentException("菜单排序不能为负数");
        }
    }
}
