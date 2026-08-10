package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleCreationCommand;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 创建自定义角色的严格 HTTP 请求，不接受租户、主体、状态或修订字段。 */
public record TenantCustomRoleCreationRequest(String name, Set<String> permissionCodes) {

    private static final Set<String> FIELDS = Set.of("name", "permissions");

    /**
     * 从严格 JSON 对象恢复创建请求。
     *
     * @throws IllegalArgumentException 当字段集合、角色名称或权限数组不合法时拒绝。
     */
    public static TenantCustomRoleCreationRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "租户自定义角色创建请求不能为空");
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("name") instanceof String name)
                || !(payload.get("permissions") instanceof List<?> values)
                || values.stream().anyMatch(value -> !(value instanceof String))) {
            throw new IllegalArgumentException("租户自定义角色创建请求字段不合法");
        }
        var permissions = values.stream().map(String.class::cast)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (permissions.size() != values.size() || permissions.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("租户自定义角色权限不能重复或为空");
        }
        return new TenantCustomRoleCreationRequest(name, permissions);
    }

    /** 转换为不含 HTTP 语义的应用命令。 */
    public TenantCustomRoleCreationCommand toCommand() {
        return new TenantCustomRoleCreationCommand(name, permissionCodes);
    }
}
