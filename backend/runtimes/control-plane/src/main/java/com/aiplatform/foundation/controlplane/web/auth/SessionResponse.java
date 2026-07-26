package com.aiplatform.foundation.controlplane.web.auth;

/**
 * 登录成功后返回的短生命周期会话信息。
 */
public record SessionResponse(
        String tokenName,
        String tokenValue,
        long expiresInSeconds) {

    @Override
    public String toString() {
        return "SessionResponse[tokenName=" + tokenName
                + ", tokenValue=[已脱敏], expiresInSeconds=" + expiresInSeconds + "]";
    }
}
