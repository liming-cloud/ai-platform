package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 角色绑定的严格 HTTP 请求，不接受租户、当前主体、权限版本或会话字段。
 */
public record TenantRoleBindingRequest(String roleCode) {

    private static final Set<String> FIELDS = Set.of("role");

    /**
     * 从严格 JSON 对象恢复角色绑定请求。
     *
     * @throws IllegalArgumentException 当字段集合或角色编码不合法时拒绝。
     */
    public static TenantRoleBindingRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "角色绑定请求不能为空");
        if (!payload.keySet().equals(FIELDS) || !(payload.get("role") instanceof String roleCode)) {
            throw new IllegalArgumentException("角色绑定请求必须且只能包含角色编码");
        }
        try {
            return new TenantRoleBindingRequest(roleCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("角色绑定请求包含非法角色", exception);
        }
    }
}
