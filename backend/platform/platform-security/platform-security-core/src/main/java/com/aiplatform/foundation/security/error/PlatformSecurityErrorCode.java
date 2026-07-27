package com.aiplatform.foundation.security.error;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/**
 * 平台安全能力的稳定错误编码。
 */
public enum PlatformSecurityErrorCode implements ErrorCode {
    INVALID_SESSION(
            "PLATFORM_SECURITY.AUTHENTICATION.0001",
            ErrorType.AUTHENTICATION,
            "需要有效会话");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    PlatformSecurityErrorCode(
            String code,
            ErrorType type,
            String defaultMessage) {
        this.code = code;
        this.type = type;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public boolean retryable() {
        return false;
    }

    @Override
    public Set<String> allowedDetailFields() {
        return Set.of();
    }
}
