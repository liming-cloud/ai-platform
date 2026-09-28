package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import java.util.Map;
import java.util.Set;

/** 严格解析三项登录策略与版本；不允许附带身份、锁定事实或安全关闭字段。 */
public record TenantLoginSecurityUpdateRequest(int sessionHours, int failedAttempts,
        int lockMinutes, long expectedVersion) {
    /** 严格拒绝缺失、未知和类型错误的字段，再交由领域对象校验范围。 */
    public static TenantLoginSecurityUpdateRequest from(Map<String, Object> payload) {
        if (payload == null || !payload.keySet().equals(Set.of(
                "sessionHours", "failedAttempts", "lockMinutes", "expectedVersion"))) {
            throw new IllegalArgumentException("登录策略字段不完整或包含未知字段");
        }
        return new TenantLoginSecurityUpdateRequest(
                integer(payload.get("sessionHours"), "会话有效期"),
                integer(payload.get("failedAttempts"), "登录失败次数"),
                integer(payload.get("lockMinutes"), "锁定时长"),
                version(payload.get("expectedVersion")));
    }

    private static int integer(Object value, String label) {
        if (!(value instanceof Integer number)) throw new IllegalArgumentException(label + "必须是整数");
        return number;
    }

    private static long version(Object value) {
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0) {
            throw new IllegalArgumentException("预期版本必须是非负整数");
        }
        return ((Number) value).longValue();
    }
}
