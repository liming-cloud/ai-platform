package com.aiplatform.foundation.organizationaccess.application.authentication.exception;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/**
 * 组织权限上下文的稳定错误编码。
 */
public enum OrganizationAccessErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(
            "ORGANIZATION_ACCESS.AUTHENTICATION.0001",
            ErrorType.AUTHENTICATION,
            "租户或登录凭据无效");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    OrganizationAccessErrorCode(
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
