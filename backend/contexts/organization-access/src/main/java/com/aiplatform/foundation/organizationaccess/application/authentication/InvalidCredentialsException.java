package com.aiplatform.foundation.organizationaccess.application.authentication;

/**
 * 不向调用方区分失败原因的统一认证异常。
 */
public final class InvalidCredentialsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("租户或登录凭据无效");
    }
}
