package com.aiplatform.foundation.security.session;

import com.aiplatform.foundation.security.error.PlatformSecurityErrorCode;
import com.aiplatform.foundation.shared.kernel.error.InfrastructureException;
import java.util.Map;

/**
 * Sa-Token 或其会话存储不可用时的安全基础设施异常。
 */
public final class AuthenticatedSessionUnavailableException
        extends InfrastructureException {

    private static final long serialVersionUID = 1L;

    public AuthenticatedSessionUnavailableException(Throwable cause) {
        super(
                PlatformSecurityErrorCode.SESSION_DEPENDENCY_UNAVAILABLE,
                Map.of(),
                cause);
    }
}
