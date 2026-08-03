package com.aiplatform.foundation.security.session;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;

/**
 * 应用层使用的独立平台认证会话端口。
 *
 * <p>它不能与租户 {@link AuthenticatedSessionGateway} 互换，避免租户令牌被解释为平台范围。</p>
 */
public interface PlatformSessionGateway {

    IssuedSession issue(PlatformRequestContext context);

    PlatformRequestContext resolve(String tokenValue);

    void revoke(String tokenValue);
}
