package com.aiplatform.foundation.security.session;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.SubjectId;
import java.time.Duration;

/**
 * 应用层使用的认证会话端口。
 */
public interface AuthenticatedSessionGateway {

    IssuedSession issue(TrustedRequestContext context);

    /** 按已验证的租户策略签发绝对有效期；适配器不支持时必须失败关闭。 */
    default IssuedSession issue(TrustedRequestContext context, Duration expiresIn) {
        throw new UnsupportedOperationException("租户会话适配器未实现策略有效期");
    }

    TrustedRequestContext resolve(String tokenValue);

    void revoke(String tokenValue);

    /**
     * 撤销指定成员的全部租户范围会话。
     *
     * <p>权限版本改变后必须调用此操作，不能等待旧令牌自然过期。</p>
     */
    void revokeMembershipSessions(MembershipId membershipId);

    /**
     * 撤销指定全局主体的全部租户范围会话。
     *
     * <p>全局账户状态、凭据或平台角色变化必须调用本操作；适配器未实现时必须失败，不能保留旧会话。</p>
     */
    default void revokeSubjectSessions(SubjectId subjectId) {
        throw new UnsupportedOperationException("租户会话适配器未实现按主体撤销");
    }
}
