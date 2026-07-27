package com.aiplatform.foundation.organizationaccess.application.authentication.exception;

import com.aiplatform.foundation.shared.kernel.error.ApplicationException;
/**
 * 不向调用方区分失败原因的统一认证异常。
 */
public final class InvalidCredentialsException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super(OrganizationAccessErrorCode.INVALID_CREDENTIALS);
    }
}
