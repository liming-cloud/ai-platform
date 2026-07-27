package com.aiplatform.foundation.security.session;

import java.time.Duration;
import java.util.Objects;

/**
 * 登录成功后返回给受信客户端的会话令牌。
 */
public record IssuedSession(
        String tokenName,
        String tokenValue,
        Duration expiresIn) {

    public IssuedSession {
        Objects.requireNonNull(tokenName, "令牌名称不能为空");
        Objects.requireNonNull(tokenValue, "令牌内容不能为空");
        Objects.requireNonNull(expiresIn, "令牌有效期不能为空");
        if (tokenName.isBlank() || tokenValue.isBlank()) {
            throw new IllegalArgumentException("令牌名称和内容不能为空");
        }
        if (expiresIn.isZero() || expiresIn.isNegative()) {
            throw new IllegalArgumentException("令牌有效期必须大于零");
        }
    }

    @Override
    public String toString() {
        return "IssuedSession[tokenName=" + tokenName + ", tokenValue=[已脱敏], expiresIn=" + expiresIn + "]";
    }
}
