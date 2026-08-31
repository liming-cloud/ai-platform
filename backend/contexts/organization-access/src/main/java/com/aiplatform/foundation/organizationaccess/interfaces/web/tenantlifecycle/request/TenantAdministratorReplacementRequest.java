package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 平台原子替换租户管理员的严格资源请求。 */
public record TenantAdministratorReplacementRequest(
        long expectedVersion,
        String replacementAccountReference) {

    private static final Set<String> FIELDS = Set.of(
            "expectedVersion", "replacementAccountReference");

    /** 只接受旧管理员版本和新账户引用。 */
    public static TenantAdministratorReplacementRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "替换租户管理员请求不能为空");
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("expectedVersion") instanceof Number version)
                || !isInteger(version)
                || version.longValue() < 0L
                || !(payload.get("replacementAccountReference") instanceof String reference)) {
            throw new IllegalArgumentException("替换租户管理员请求包含未允许字段或无效字段");
        }
        return new TenantAdministratorReplacementRequest(version.longValue(), reference);
    }

    /** 将浏览器安全字符串转换为应用层主体标识。 */
    public long replacementAccountReferenceValue() {
        try {
            return Long.parseLong(replacementAccountReference);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("替换账户引用必须是十进制正整数", exception);
        }
    }

    private static boolean isInteger(Number value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long;
    }
}
