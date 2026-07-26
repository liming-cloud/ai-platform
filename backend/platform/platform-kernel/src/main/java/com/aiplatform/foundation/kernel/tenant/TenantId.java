package com.aiplatform.foundation.kernel.tenant;

import java.util.Objects;
import java.util.UUID;

/**
 * 平台范围内不可变的租户标识。
 *
 * <p>租户标识必须由可信认证上下文提供，业务调用方不得使用空值表示平台级访问。</p>
 */
public record TenantId(UUID value) {

    public TenantId {
        Objects.requireNonNull(value, "租户标识不能为空");
    }

    public static TenantId parse(String value) {
        Objects.requireNonNull(value, "租户标识文本不能为空");
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
