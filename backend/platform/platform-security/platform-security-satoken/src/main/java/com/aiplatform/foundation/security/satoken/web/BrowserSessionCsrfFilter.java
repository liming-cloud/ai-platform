package com.aiplatform.foundation.security.satoken.web;

import com.aiplatform.foundation.security.web.BrowserSessionCookieNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 对浏览器范围会话的非安全请求执行同源和 CSRF 双重失败关闭校验。
 *
 * <p>机器 Bearer 调用不携带范围 cookie，因此保持既有契约；浏览器登录本身也必须先取得非身份 CSRF
 * cookie，避免首次建立会话成为跨站写入入口。</p>
 */
public final class BrowserSessionCsrfFilter extends OncePerRequestFilter {

    private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String BROWSER_LOGIN_PATH = "/api/v1/auth/browser-sessions";
    private static final String CSRF_HEADER = "X-CSRF-Token";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!UNSAFE_METHODS.contains(request.getMethod())
                || (!BROWSER_LOGIN_PATH.equals(path(request)) && !hasBrowserSession(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!isSameOrigin(request) || !matchesCsrf(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean hasBrowserSession(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (BrowserSessionCookieNames.isSessionCookie(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSameOrigin(HttpServletRequest request) {
        var origin = request.getHeader(HttpHeaders.ORIGIN);
        var fetchSite = request.getHeader("Sec-Fetch-Site");
        return origin != null
                && origin.equals(request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                ? "" : ":" + request.getServerPort()))
                && "same-origin".equals(fetchSite);
    }

    private static boolean matchesCsrf(HttpServletRequest request) {
        var expected = cookieValue(request, BrowserSessionCookieNames.CSRF);
        var actual = request.getHeader(CSRF_HEADER);
        return expected != null && expected.equals(actual);
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
