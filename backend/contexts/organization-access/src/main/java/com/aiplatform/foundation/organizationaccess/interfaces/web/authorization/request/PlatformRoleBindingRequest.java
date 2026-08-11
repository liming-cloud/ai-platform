package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 平台固定角色绑定的严格 HTTP 请求，不接受身份、权限或会话字段。 */
public record PlatformRoleBindingRequest(String roleCode) {

    private static final Set<String> FIELDS = Set.of("role");

    /** 从仅包含角色代码的 JSON 对象恢复请求。 */
    public static PlatformRoleBindingRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "平台角色绑定请求不能为空");
        if (!payload.keySet().equals(FIELDS) || !(payload.get("role") instanceof String roleCode)) {
            throw new IllegalArgumentException("平台角色绑定请求必须且只能包含角色编码");
        }
        return new PlatformRoleBindingRequest(roleCode);
    }
}
