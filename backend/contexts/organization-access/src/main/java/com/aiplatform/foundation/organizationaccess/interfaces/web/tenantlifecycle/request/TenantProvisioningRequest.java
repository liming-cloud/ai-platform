package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 平台建立租户的严格业务请求。
 *
 * <p>初始管理员主体是业务资源目标，不是当前操作者；当前操作者、权限、令牌和范围字段一律不接受。</p>
 */
public record TenantProvisioningRequest(
        String tenantCode,
        String displayName,
        String initialAdministratorSubjectId) {

    private static final Set<String> FIELDS = Set.of(
            "tenantCode", "displayName", "initialAdministratorSubjectId");

    /**
     * 从 HTTP JSON 对象构造严格请求，拒绝任何未声明字段。
     */
    public static TenantProvisioningRequest from(Map<String, Object> payload) {
        payload = Objects.requireNonNull(payload, "创建租户请求不能为空");
        if (!payload.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("创建租户请求包含未允许字段或缺失必填字段");
        }
        return new TenantProvisioningRequest(
                stringField(payload, "tenantCode"),
                stringField(payload, "displayName"),
                stringField(payload, "initialAdministratorSubjectId"));
    }

    /**
     * 返回可交给应用层验证的十进制主体标识。
     */
    public long initialAdministratorSubjectIdValue() {
        try {
            return Long.parseLong(initialAdministratorSubjectId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("初始管理员主体标识必须是十进制正整数", exception);
        }
    }

    private static String stringField(Map<String, Object> payload, String field) {
        var value = payload.get(field);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("创建租户请求字段必须为文本");
        }
        return text;
    }
}
