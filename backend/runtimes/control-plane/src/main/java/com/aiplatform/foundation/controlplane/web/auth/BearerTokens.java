package com.aiplatform.foundation.controlplane.web.auth;

import com.aiplatform.foundation.organizationaccess.adapter.out.session.InvalidAuthenticatedSessionException;

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
        var token = authorization.substring(PREFIX.length());
        if (token.isBlank()
                || token.length() > MAX_TOKEN_LENGTH
                || !token.equals(token.trim())
                || token.indexOf(' ') >= 0) {
            throw new InvalidAuthenticatedSessionException();
        }
        return token;
    }
}
