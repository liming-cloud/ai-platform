package com.aiplatform.foundation.security.session;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;

/**
 * 应用层使用的认证会话端口。
 */
public interface AuthenticatedSessionGateway {

    IssuedSession issue(TrustedRequestContext context);

    TrustedRequestContext resolve(String tokenValue);

    void revoke(String tokenValue);
}
