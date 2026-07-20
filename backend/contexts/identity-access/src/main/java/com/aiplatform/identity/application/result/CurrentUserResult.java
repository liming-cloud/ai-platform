package com.aiplatform.identity.application.result;

import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.PermissionCode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 当前登录用户查询结果，返回会话可用性判断所需的最小身份、角色和令牌过期时间。
 */
public record CurrentUserResult(
        TenantId tenantId,
        String userId,
        String loginName,
        String displayName,
        Set<String> roleCodes,
        Set<PermissionCode> permissionCodes,
        List<CurrentUserMenuResult> menus,
        String defaultRoute,
        Instant accessTokenExpiresAt
) {
    public CurrentUserResult {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        userId = requireText(userId, "用户 ID");
        loginName = requireText(loginName, "登录名");
        displayName = requireText(displayName, "用户显示名");
        roleCodes = normalizeRoleCodes(roleCodes);
        permissionCodes = normalizePermissionCodes(permissionCodes);
        menus = normalizeMenus(menus);
        defaultRoute = requireText(defaultRoute, "默认路由");
        Objects.requireNonNull(accessTokenExpiresAt, "令牌过期时间不能为空");
    }

    private static Set<String> normalizeRoleCodes(Set<String> values) {
        Objects.requireNonNull(values, "角色编码集合不能为空");
        Set<String> normalized = values.stream()
                .map(value -> requireText(value, "角色编码"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("角色编码集合不能为空");
        }
        return normalized;
    }

    private static Set<PermissionCode> normalizePermissionCodes(Set<PermissionCode> values) {
        Objects.requireNonNull(values, "权限编码集合不能为空");
        Set<PermissionCode> normalized = values.stream()
                .map(value -> Objects.requireNonNull(value, "权限编码不能为空"))
                .collect(Collectors.toUnmodifiableSet());
        return normalized;
    }

    private static List<CurrentUserMenuResult> normalizeMenus(List<CurrentUserMenuResult> values) {
        Objects.requireNonNull(values, "菜单列表不能为空");
        return values.stream()
                .map(value -> Objects.requireNonNull(value, "菜单项不能为空"))
                .toList();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
