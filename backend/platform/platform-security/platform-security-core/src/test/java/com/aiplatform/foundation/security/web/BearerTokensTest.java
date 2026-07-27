package com.aiplatform.foundation.security.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import org.junit.jupiter.api.Test;

class BearerTokensTest {

    @Test
    void 应只接受严格Bearer令牌() {
        assertEquals("token-value", BearerTokens.require("Bearer token-value"));
    }

    @Test
    void 应拒绝缺失空白和包含空格的令牌() {
        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> BearerTokens.require(null));
        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> BearerTokens.require("Bearer "));
        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> BearerTokens.require("Bearer token value"));
    }
}
