package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 平台变更租户管理员状态时使用的严格乐观锁请求。 */
public record TenantAdministratorVersionRequest(long expectedVersion) {

    private static final Set<String> FIELDS = Set.of("expectedVersion");

    /** 只接受非负整型版本，不接受身份和权限字段。 */
    public static TenantAdministratorVersionRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "租户管理员状态请求不能为空");
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("expectedVersion") instanceof Number version)
                || !isInteger(version)
                || version.longValue() < 0L) {
            throw new IllegalArgumentException("租户管理员状态请求包含未允许字段或无效版本");
        }
        return new TenantAdministratorVersionRequest(version.longValue());
    }

    private static boolean isInteger(Number value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long;
    }
}
