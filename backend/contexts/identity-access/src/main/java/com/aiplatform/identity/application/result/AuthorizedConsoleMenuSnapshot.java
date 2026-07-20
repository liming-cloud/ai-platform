package com.aiplatform.identity.application.result;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 当前用户控制台菜单授权快照，封装默认路由解析规则，避免 presentation 层生成授权事实。
 */
public record AuthorizedConsoleMenuSnapshot(
        List<CurrentUserMenuResult> menus,
        String defaultRoute
) {
    private static final String FORBIDDEN_ROUTE = "/403";

    public AuthorizedConsoleMenuSnapshot {
        menus = Objects.requireNonNull(menus, "授权菜单列表不能为空").stream()
                .map(menu -> Objects.requireNonNull(menu, "授权菜单项不能为空"))
                .sorted(Comparator.comparingInt(CurrentUserMenuResult::sort).thenComparing(CurrentUserMenuResult::code))
                .toList();
        defaultRoute = requireDefaultRoute(defaultRoute);
    }

    public static AuthorizedConsoleMenuSnapshot from(
            List<CurrentUserMenuResult> menus,
            List<String> defaultMenuCodes
    ) {
        List<CurrentUserMenuResult> normalizedMenus = Objects.requireNonNull(menus, "授权菜单列表不能为空").stream()
                .map(menu -> Objects.requireNonNull(menu, "授权菜单项不能为空"))
                .sorted(Comparator.comparingInt(CurrentUserMenuResult::sort).thenComparing(CurrentUserMenuResult::code))
                .toList();
        Set<String> effectiveMenuCodes = normalizedMenus.stream()
                .map(CurrentUserMenuResult::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        String route = Objects.requireNonNull(defaultMenuCodes, "默认菜单编码列表不能为空").stream()
                .map(code -> requireText(code, "默认菜单编码"))
                .filter(effectiveMenuCodes::contains)
                .findFirst()
                .flatMap(defaultCode -> normalizedMenus.stream()
                        .filter(menu -> menu.code().equals(defaultCode))
                        .findFirst()
                        .map(CurrentUserMenuResult::path))
                .orElseGet(() -> normalizedMenus.stream()
                        .findFirst()
                        .map(CurrentUserMenuResult::path)
                        .orElse(FORBIDDEN_ROUTE));
        return new AuthorizedConsoleMenuSnapshot(normalizedMenus, route);
    }

    private static String requireDefaultRoute(String value) {
        return requireText(value, "默认路由");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
