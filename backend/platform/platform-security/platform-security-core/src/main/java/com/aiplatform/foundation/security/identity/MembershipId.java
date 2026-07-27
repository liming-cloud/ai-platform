package com.aiplatform.foundation.security.identity;

/**
 * 主体在一个租户内的成员身份标识。
 */
public record MembershipId(long value) {

    public MembershipId {
        if (value <= 0) {
            throw new IllegalArgumentException("成员标识必须为正数");
        }
    }

    public static MembershipId parse(String value) {
        try {
            return new MembershipId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("成员标识必须是十进制正整数", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
