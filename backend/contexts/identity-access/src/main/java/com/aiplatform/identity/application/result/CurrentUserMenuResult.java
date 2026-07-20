package com.aiplatform.identity.application.result;

import java.util.Objects;

/**
 * 当前用户可见控制台菜单，来自服务端 RBAC、角色菜单和租户功能事实源。
 */
public record CurrentUserMenuResult(
        String code,
        String name,
        String platform,
        String path,
        String routeName,
        String componentKey,
        String permission,
        int sort
) {
    public CurrentUserMenuResult {
        code = requireText(code, "菜单编码");
        name = requireText(name, "菜单名称");
        platform = requireText(platform, "菜单平台");
        path = requireText(path, "菜单路径");
        routeName = requireText(routeName, "路由名称");
        componentKey = requireText(componentKey, "组件键");
        permission = requireText(permission, "菜单权限");
        if (sort < 0) {
            throw new IllegalArgumentException("菜单排序不能小于 0");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
