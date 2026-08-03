package com.aiplatform.foundation.organizationaccess.application.authentication.service;

import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;

/**
 * 撤销当前浏览器登录族的两个独立范围会话，范围令牌仍不得互相解释。
 */
public final class BrowserSessionTerminationService {

    private final AuthenticatedSessionGateway tenantSessions;
    private final PlatformSessionGateway platformSessions;

    public BrowserSessionTerminationService(
            AuthenticatedSessionGateway tenantSessions,
            PlatformSessionGateway platformSessions) {
        this.tenantSessions = java.util.Objects.requireNonNull(tenantSessions, "租户会话网关不能为空");
        this.platformSessions = java.util.Objects.requireNonNull(platformSessions, "平台会话网关不能为空");
    }

    /**
     * 只撤销各自 cookie 提供的范围会话；缺少某一范围不会妨碍另一范围注销。
     */
    public void terminate(String tenantSessionToken, String platformSessionToken) {
        if (tenantSessionToken != null) {
            tenantSessions.revoke(tenantSessionToken);
        }
        if (platformSessionToken != null) {
            platformSessions.revoke(platformSessionToken);
        }
    }
}
