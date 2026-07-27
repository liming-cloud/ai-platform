package com.aiplatform.foundation.organizationaccess.domain.authentication;

import java.util.Objects;

/**
 * 只允许用于等价校验且输出始终脱敏的密码摘要值对象。
 */
public final class EncodedPassword {

    private static final int MAXIMUM_LENGTH = 256;
    private static final String REDACTED = "[已脱敏]";

    private final String value;

    public EncodedPassword(String value) {
        Objects.requireNonNull(value, "密码摘要不能为空");
        if (value.isBlank() || value.length() > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("密码摘要格式不正确");
        }
        this.value = value;
    }

    /**
     * 仅供认证应用服务交给密码校验端口使用。
     */
    public String verificationValue() {
        return value;
    }

    @Override
    public String toString() {
        return REDACTED;
    }
}
