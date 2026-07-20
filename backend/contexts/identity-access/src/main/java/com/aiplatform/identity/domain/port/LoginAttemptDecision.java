package com.aiplatform.identity.domain.port;

/**
 * 登录尝试防护端口的稳定决策，不向应用层暴露计数器、Redis key 或异常类型。
 */
public enum LoginAttemptDecision {
    ALLOWED,
    RATE_LIMITED,
    UNAVAILABLE
}
