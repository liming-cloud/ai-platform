package com.aiplatform.foundation.organizationaccess.application.authorization.exception;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/** 组织权限治理用例的稳定错误编码。 */
public enum OrganizationAccessAuthorizationErrorCode implements ErrorCode {
    ACCOUNT_LOGIN_NAME_CONFLICT(
            "ORGANIZATION_ACCESS.CONFLICT.0001",
            ErrorType.CONFLICT,
            "全局登录名已经存在");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    OrganizationAccessAuthorizationErrorCode(String code, ErrorType type, String defaultMessage) {
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
