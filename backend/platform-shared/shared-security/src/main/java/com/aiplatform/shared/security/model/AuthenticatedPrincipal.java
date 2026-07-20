package com.aiplatform.shared.security.model;

import com.aiplatform.shared.kernel.TenantId;

public record AuthenticatedPrincipal(TenantId tenantId, String actorType, String actorId) {
    public AuthenticatedPrincipal {
        tenantId = requireValue(tenantId, "租户 ID");
        actorType = requireText(actorType, "主体类型");
        actorId = requireText(actorId, "主体 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
