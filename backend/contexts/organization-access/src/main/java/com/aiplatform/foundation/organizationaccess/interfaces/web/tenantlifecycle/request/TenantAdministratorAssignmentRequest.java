package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 平台分配租户管理员的严格资源请求。 */
public record TenantAdministratorAssignmentRequest(String accountReference) {

    private static final Set<String> FIELDS = Set.of("accountReference");

    /** 拒绝当前操作者、权限、租户范围和其他未声明字段。 */
    public static TenantAdministratorAssignmentRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "分配租户管理员请求不能为空");
        if (!payload.keySet().equals(FIELDS)
                || !(payload.get("accountReference") instanceof String reference)) {
            throw new IllegalArgumentException("分配租户管理员请求包含未允许字段或缺失必填字段");
        }
        return new TenantAdministratorAssignmentRequest(reference);
    }

    /** 将浏览器安全字符串转换为应用层主体标识。 */
    public long accountReferenceValue() {
        try {
            return Long.parseLong(accountReference);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("账户引用必须是十进制正整数", exception);
        }
    }
}
