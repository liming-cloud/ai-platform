package com.aiplatform.foundation.organizationaccess.application.authentication.dto;

import com.aiplatform.foundation.security.session.IssuedSession;
import java.util.Objects;
import java.util.Optional;

/**
 * 一次浏览器登录成功签发的范围会话；只供 HTTP 层写入 HttpOnly cookie。
 *
 * <p>该结果不得序列化、记录日志或返回给浏览器，避免 token 跨越受限 HTTP 响应边界。</p>
 */
public final class BrowserSessionIssue {

    private final Optional<IssuedSession> tenantSession;
    private final Optional<IssuedSession> platformSession;

    public BrowserSessionIssue(
            Optional<IssuedSession> tenantSession,
            Optional<IssuedSession> platformSession) {
        this.tenantSession = Objects.requireNonNull(tenantSession, "租户会话结果不能为空");
        this.platformSession = Objects.requireNonNull(platformSession, "平台会话结果不能为空");
        if (tenantSession.isEmpty() && platformSession.isEmpty()) {
            throw new IllegalArgumentException("浏览器登录必须至少签发一种范围会话");
        }
    }

    public Optional<IssuedSession> tenantSession() {
        return tenantSession;
    }

    public Optional<IssuedSession> platformSession() {
        return platformSession;
    }

    @Override
    public String toString() {
        return "BrowserSessionIssue[tenantSession="
                + (tenantSession.isPresent() ? "[已脱敏]" : "无")
                + ", platformSession=" + (platformSession.isPresent() ? "[已脱敏]" : "无") + "]";
    }
}
