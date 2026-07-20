package com.aiplatform.audit.infrastructure.persistence.entity.support;

/**
 * 审计表主键字段解析器，区分未知主体和错误主体，禁止非法 ID 被静默写成空值。
 */
public final class AuditNumericIdParser {
    private AuditNumericIdParser() {
    }

    public static Long parseRequiredId(String value, String fieldName) {
        Long parsed = parseOptionalId(value, fieldName);
        if (parsed == null) {
            throw new IllegalArgumentException(fieldName + "必须是数字型数据库主键");
        }
        return parsed;
    }

    public static Long parseOptionalId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + "必须是数字型数据库主键", exception);
        }
    }

    /**
     * 为 opaque 资源引用生成旧 BIGINT 列的兼容投影；非数字引用是合法事实，不应导致审计失败。
     */
    public static Long projectOptionalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
