package com.aiplatform.identity.domain.permission;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.Objects;

/**
 * 资源范围值对象，表达权限授权适用的资源类型和资源实例。
 */
public record ResourceScope(String resourceType, String resourceId) implements ValueObject {
    public ResourceScope {
        resourceType = requireText(resourceType, "资源类型");
        resourceId = requireText(resourceId, "资源 ID");
    }

    public boolean matches(String type, String id) {
        return Objects.equals(resourceType, type) && Objects.equals(resourceId, id);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
