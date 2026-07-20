package com.aiplatform.shared.kernel;

/**
 * 数据库数字主键解析器，统一持久化边界的必填 ID 失败关闭语义。
 */
public final class DatabaseIdParser {
    private DatabaseIdParser() {
    }

    public static Long parseRequiredLong(String value, String fieldName) {
        String normalized = requireText(value, fieldName);
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " 必须是数字型数据库主键", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
