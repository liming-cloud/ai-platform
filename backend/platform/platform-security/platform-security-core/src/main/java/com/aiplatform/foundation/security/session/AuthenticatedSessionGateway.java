package com.aiplatform.foundation.security.session;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.identity.MembershipId;

/**
 * 应用层使用的认证会话端口。
 */
public interface AuthenticatedSessionGateway {

    IssuedSession issue(TrustedRequestContext context);

    TrustedRequestContext resolve(String tokenValue);

    void revoke(String tokenValue);

    /**
     * 撤销指定成员的全部租户范围会话。
     *
     * <p>权限版本改变后必须调用此操作，不能等待旧令牌自然过期。</p>
     */
    void revokeMembershipSessions(MembershipId membershipId);
}
