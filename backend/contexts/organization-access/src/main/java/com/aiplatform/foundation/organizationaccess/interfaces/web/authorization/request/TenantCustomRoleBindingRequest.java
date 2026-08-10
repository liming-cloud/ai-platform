package com.aiplatform.foundation.organizationaccess.interfaces.web.authorization.request;

import com.aiplatform.foundation.organizationaccess.application.authorization.command.TenantCustomRoleBindingCommand;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 自定义角色绑定的严格 HTTP 请求，不接受租户、主体、权限版本或会话字段。 */
public record TenantCustomRoleBindingRequest(long customRoleId) {

    private static final Set<String> FIELDS = Set.of("customRoleId");

    /**
     * 从严格 JSON 对象恢复自定义角色绑定请求。
     *
     * @throws IllegalArgumentException 当字段集合或角色标识不合法时拒绝。
     */
    public static TenantCustomRoleBindingRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "自定义角色绑定请求不能为空");
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("customRoleId") instanceof Number rawId)
                || rawId instanceof Float
                || rawId instanceof Double
                || rawId.longValue() <= 0L) {
            throw new IllegalArgumentException("自定义角色绑定请求必须且只能包含正整数角色标识");
        }
        return new TenantCustomRoleBindingRequest(rawId.longValue());
    }

    /** 转换为不含 HTTP 语义的应用命令。 */
    public TenantCustomRoleBindingCommand toCommand(long membershipId) {
        return new TenantCustomRoleBindingCommand(membershipId, customRoleId);
    }
}
