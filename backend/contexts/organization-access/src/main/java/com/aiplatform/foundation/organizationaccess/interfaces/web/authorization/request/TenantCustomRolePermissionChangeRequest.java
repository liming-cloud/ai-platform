package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRolePermissionChangeCommand;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 修改自定义角色权限的严格 HTTP 请求，不接受租户、主体、状态或修订字段。 */
public record TenantCustomRolePermissionChangeRequest(Set<String> permissionCodes) {

    private static final Set<String> FIELDS = Set.of("permissions");

    /**
     * 从严格 JSON 对象恢复权限变更请求。
     *
     * @throws IllegalArgumentException 当字段集合或权限数组不合法时拒绝。
     */
    public static TenantCustomRolePermissionChangeRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "租户自定义角色权限变更请求不能为空");
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("permissions") instanceof List<?> values)
                || values.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException("租户自定义角色权限变更请求字段不合法");
        }
        var permissions = values.stream().map(String.class::cast)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (permissions.size() != values.size() || permissions.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("租户自定义角色权限不能重复或为空");
        }
        return new TenantCustomRolePermissionChangeRequest(permissions);
    }

    /** 转换为不含 HTTP 语义的应用命令。 */
    public TenantCustomRolePermissionChangeCommand toCommand(long customRoleId) {
        return new TenantCustomRolePermissionChangeCommand(customRoleId, permissionCodes);
    }
}
