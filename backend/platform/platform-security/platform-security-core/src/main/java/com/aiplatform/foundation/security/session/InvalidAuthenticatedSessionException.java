package com.aiplatform.foundation.security.session;

import com.aiplatform.foundation.security.error.PlatformSecurityErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 会话无效、已撤销或内容无法形成可信请求上下文。
 */
public final class InvalidAuthenticatedSessionException
        extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public InvalidAuthenticatedSessionException() {
        super(PlatformSecurityErrorCode.INVALID_SESSION);
    }
}
