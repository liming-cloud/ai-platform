package com.aiplatform.infra.satoken.web.permission.model;

import cn.dev33.satoken.router.SaHttpMethod;
import com.aiplatform.shared.security.model.PermissionCode;

/**
 * 单条路由权限规则，只保存归一化后的 HTTP 方法、路径模式和权限码。
 */
public record RoutePermissionRule(String method, String pattern, String permission) {
    public RoutePermissionRule {
        method = SaHttpMethod.toEnum(requireText(method, "HTTP 方法")).name();
        pattern = requireText(pattern, "路由路径");
        permission = new PermissionCode(requireText(permission, "权限编码")).value();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
