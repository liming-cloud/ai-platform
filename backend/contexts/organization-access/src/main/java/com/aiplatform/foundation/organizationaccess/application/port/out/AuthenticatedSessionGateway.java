package com.aiplatform.foundation.organizationaccess.application.port.out;

import com.aiplatform.foundation.organizationaccess.api.model.IssuedSession;
import com.aiplatform.foundation.organizationaccess.api.model.TrustedRequestContext;

/**
 * 应用层使用的认证会话端口。
 */
public interface AuthenticatedSessionGateway {

    IssuedSession issue(TrustedRequestContext context);

    TrustedRequestContext resolve(String tokenValue);

    void revoke(String tokenValue);
}
