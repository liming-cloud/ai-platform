package com.aiplatform.identity.presentation.dto;

import com.aiplatform.identity.application.result.CurrentUserResult;
import java.time.Instant;
import java.util.List;

public record CurrentUserResponse(
        String tenantId,
        String userId,
        String loginName,
        String displayName,
        List<String> roleCodes,
        List<String> permissionCodes,
        List<ConsoleMenuItem> menus,
        String defaultRoute,
        Instant accessTokenExpiresAt
) {
    public CurrentUserResponse {
        tenantId = IdentityResponseContracts.requireText(tenantId, "租户 ID");
        userId = IdentityResponseContracts.requireText(userId, "用户 ID");
        loginName = IdentityResponseContracts.requireText(loginName, "登录名");
        displayName = IdentityResponseContracts.requireText(displayName, "用户展示名");
        roleCodes = IdentityResponseContracts.normalizeRoleCodeList(roleCodes);
        permissionCodes = normalizePermissionCodeList(permissionCodes);
        menus = normalizeMenus(menus);
        defaultRoute = IdentityResponseContracts.requireText(defaultRoute, "默认路由");
        accessTokenExpiresAt = IdentityResponseContracts.requireValue(accessTokenExpiresAt, "令牌过期时间");
    }

    public static CurrentUserResponse from(CurrentUserResult result) {
        return new CurrentUserResponse(
                result.tenantId().value(),
                result.userId(),
                result.loginName(),
                result.displayName(),
                result.roleCodes().stream().toList(),
                result.permissionCodes().stream().map(permissionCode -> permissionCode.value()).toList(),
                result.menus().stream()
                        .map(menu -> new ConsoleMenuItem(
                                menu.code(),
                                menu.name(),
                                menu.platform(),
                                menu.path(),
                                menu.routeName(),
                                menu.componentKey(),
                                menu.permission(),
                                menu.sort()
                        ))
                        .toList(),
                result.defaultRoute(),
                result.accessTokenExpiresAt()
        );
    }

    private static List<String> normalizePermissionCodeList(List<String> values) {
        return values.stream()
                .map(value -> IdentityResponseContracts.requireText(value, "权限编码"))
                .distinct()
                .sorted()
                .toList();
    }

    private static List<ConsoleMenuItem> normalizeMenus(List<ConsoleMenuItem> values) {
        List<ConsoleMenuItem> normalized = IdentityResponseContracts.requireValue(values, "菜单列表").stream()
                .map(value -> IdentityResponseContracts.requireValue(value, "菜单项"))
                .sorted(java.util.Comparator.comparingInt(ConsoleMenuItem::sort))
                .toList();
        return List.copyOf(normalized);
    }

}
