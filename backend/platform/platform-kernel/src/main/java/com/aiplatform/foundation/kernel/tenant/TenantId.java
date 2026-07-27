package com.aiplatform.foundation.kernel.tenant;

/**
 * 平台范围内不可变的租户标识。
 *
 * <p>租户标识必须由可信认证上下文提供，业务调用方不得使用空值表示平台级访问。</p>
 */
public record TenantId(long value) {

    public TenantId {
        if (value <= 0) {
            throw new IllegalArgumentException("租户标识必须为正数");
        }
    }

    public static TenantId parse(String value) {
        try {
            return new TenantId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("租户标识必须是十进制正整数", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
