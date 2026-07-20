package com.aiplatform.shared.security.model;

import java.util.Locale;

public record ResourceAclSubject(String subjectType, String subjectId) {
    public ResourceAclSubject {
        subjectType = requireText(subjectType, "ACL 主体类型").toLowerCase(Locale.ROOT);
        subjectId = requireText(subjectId, "ACL 主体 ID");
        if (!"user".equals(subjectType) && !"role".equals(subjectType)) {
            throw new IllegalArgumentException("ACL 主体类型只能是 user 或 role");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
