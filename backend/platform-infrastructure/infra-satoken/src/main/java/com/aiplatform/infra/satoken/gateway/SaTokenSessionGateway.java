package com.aiplatform.infra.satoken.gateway;

import java.util.Optional;

/**
 * Sa-Token 会话网关，隔离业务上下文与 Sa-Token 静态工具类。
 */
public interface SaTokenSessionGateway {
    String createLoginSession(String loginId, long timeoutSeconds);

    Optional<String> findLoginIdByToken(String tokenValue);

    long getTokenTimeoutSeconds(String tokenValue);

    void revokeToken(String tokenValue);
}
