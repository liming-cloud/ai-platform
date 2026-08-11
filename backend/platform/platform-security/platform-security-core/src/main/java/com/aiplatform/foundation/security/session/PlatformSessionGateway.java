package com.aiplatform.foundation.security.session;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;

/**
 * 应用层使用的独立平台认证会话端口。
 *
 * <p>它不能与租户 {@link AuthenticatedSessionGateway} 互换，避免租户令牌被解释为平台范围。</p>
 */
public interface PlatformSessionGateway {

    IssuedSession issue(PlatformRequestContext context);

    PlatformRequestContext resolve(String tokenValue);

    void revoke(String tokenValue);

    /**
     * 撤销一个全局主体签发的全部平台范围会话。
     *
     * <p>账户状态、凭据或平台角色变更必须调用本操作；未实现的适配器必须失败而不能静默保留旧会话。</p>
     */
    default void revokeSubjectSessions(SubjectId subjectId) {
        throw new UnsupportedOperationException("平台会话适配器未实现按主体撤销");
    }
}
