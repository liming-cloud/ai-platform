package com.aiplatform.foundation.security.web;

import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;

/**
 * 统一解析严格的 Bearer 认证头。
 */
public final class BearerTokens {

    private static final String PREFIX = "Bearer ";
    private static final int MAX_TOKEN_LENGTH = 512;

    private BearerTokens() {
    }

    public static String require(String authorization) {
        if (authorization == null
                || !authorization.startsWith(PREFIX)
                || authorization.length() <= PREFIX.length()) {
            throw new InvalidAuthenticatedSessionException();
        }
        return requireTokenValue(authorization.substring(PREFIX.length()));
    }

    /**
     * 校验已由受限协议位置取得的会话 token 值。
     *
     * @throws InvalidAuthenticatedSessionException 当 token 缺失、包含空白或超过长度上限时拒绝。
     */
    public static String requireTokenValue(String token) {
        if (token.isBlank()
                || token.length() > MAX_TOKEN_LENGTH
                || !token.equals(token.trim())
                || token.indexOf(' ') >= 0) {
            throw new InvalidAuthenticatedSessionException();
        }
        return token;
    }
}
