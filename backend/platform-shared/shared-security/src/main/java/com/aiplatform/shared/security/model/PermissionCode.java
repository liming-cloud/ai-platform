package com.aiplatform.shared.security.model;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.regex.Pattern;

/**
 * 权限编码值对象，统一约束 RBAC 权限点使用 context:resource:action 格式。
 */
public record PermissionCode(String value) implements ValueObject {
    private static final Pattern PATTERN = Pattern.compile("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*");

    public PermissionCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("权限编码不能为空");
        }
        value = value.trim();
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("权限编码必须符合 context:resource:action 格式");
        }
    }
}
