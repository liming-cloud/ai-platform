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
            "需要有效会话",
            false),
    SESSION_DEPENDENCY_UNAVAILABLE(
            "PLATFORM_SECURITY.DEPENDENCY_UNAVAILABLE.0001",
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "会话服务暂时不可用",
            true);

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;
    private final boolean retryable;

    PlatformSecurityErrorCode(
            String code,
            ErrorType type,
            String defaultMessage,
            boolean retryable) {
        this.code = code;
        this.type = type;
        this.defaultMessage = defaultMessage;
        this.retryable = retryable;
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
        return retryable;
    }

    @Override
    public Set<String> allowedDetailFields() {
        return Set.of();
    }
}
