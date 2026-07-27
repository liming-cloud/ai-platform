package com.aiplatform.foundation.security.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 使用“资源:动作”表达的稳定权限编码。
 */
public record PermissionCode(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("[a-z][a-z0-9-]{1,63}:[a-z][a-z0-9-]{1,63}");

    public PermissionCode {
        Objects.requireNonNull(value, "权限编码不能为空");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("权限编码必须使用小写的资源:动作格式");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
