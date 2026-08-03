package com.aiplatform.foundation.security.satoken.web;

import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.security.web.BearerTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * 从机器 Bearer 或限定名称的浏览器 cookie 恢复对应范围的会话 token。
 *
 * <p>Bearer 兼容既有机器调用；没有 Bearer 时只读取调用方指定的 cookie 名称，绝不把另一范围 cookie
 * 当作回退。cookie 值仍使用与 Bearer 相同的严格 token 校验。</p>
 */
final class BrowserSessionTokenResolver {

    private BrowserSessionTokenResolver() {
    }

    static String require(NativeWebRequest request, String cookieName) {
        var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            return BearerTokens.require(authorization);
        }
        var servletRequest = request.getNativeRequest(HttpServletRequest.class);
        if (servletRequest == null || servletRequest.getCookies() == null) {
            throw new InvalidAuthenticatedSessionException();
        }
        for (Cookie cookie : servletRequest.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return BearerTokens.requireTokenValue(cookie.getValue());
            }
        }
        throw new InvalidAuthenticatedSessionException();
    }
}
