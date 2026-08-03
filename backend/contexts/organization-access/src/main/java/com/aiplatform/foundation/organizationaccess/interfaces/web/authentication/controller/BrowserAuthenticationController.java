package com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller;

import com.aiplatform.foundation.organizationaccess.application.authentication.command.BrowserLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionIssue;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserAuthenticationService;
import com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.request.BrowserLoginRequest;
import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.session.IssuedSession;
import com.aiplatform.foundation.security.web.BrowserSessionCookieNames;
import com.aiplatform.foundation.shared.kernel.identifier.EntityIdGenerator;
import com.aiplatform.foundation.shared.kernel.identifier.TraceIds;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 同源浏览器登录入口，只用 HttpOnly cookie 交付范围会话，不向浏览器响应体泄露 token。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class BrowserAuthenticationController {

    private static final SecureRandom CSRF_RANDOM = new SecureRandom();

    private final BrowserAuthenticationService authentication;
    private final EntityIdGenerator identifiers;

    public BrowserAuthenticationController(
            BrowserAuthenticationService authentication,
            EntityIdGenerator identifiers) {
        this.authentication = authentication;
        this.identifiers = identifiers;
    }

    /**
     * 签发首次浏览器登录所需的非身份 CSRF 防护值。
     */
    @GetMapping("/browser-csrf")
    public ResponseEntity<Void> issueCsrf() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, csrfCookie(newCsrfToken(), Duration.ofHours(1)).toString())
                .build();
    }

    /**
     * 通过一次身份验证签发已验证角色对应的独立浏览器范围会话。
     */
    @PostMapping("/browser-sessions")
    public ResponseEntity<Void> login(@RequestBody BrowserLoginRequest request) {
        var password = request.passwordCopy();
        try (var command = new BrowserLoginCommand(
                request.tenantCode(),
                request.loginName(),
                password,
                AccessChannel.CONSOLE,
                TraceIds.next(identifiers))) {
            var issue = authentication.authenticate(command);
            return responseWithCookies(issue);
        } finally {
            Arrays.fill(password, '\0');
            request.clearPassword();
        }
    }

    private static ResponseEntity<Void> responseWithCookies(BrowserSessionIssue issue) {
        var response = ResponseEntity.noContent();
        issue.tenantSession().ifPresent(session -> response.header(
                HttpHeaders.SET_COOKIE,
                sessionCookie(BrowserSessionCookieNames.TENANT_SESSION, session).toString()));
        issue.platformSession().ifPresent(session -> response.header(
                HttpHeaders.SET_COOKIE,
                sessionCookie(BrowserSessionCookieNames.PLATFORM_SESSION, session).toString()));
        var expiry = issue.tenantSession()
                .or(issue::platformSession)
                .orElseThrow()
                .expiresIn();
        return response.header(HttpHeaders.SET_COOKIE, csrfCookie(newCsrfToken(), expiry).toString()).build();
    }

    private static ResponseCookie sessionCookie(String name, IssuedSession session) {
        return ResponseCookie.from(name, session.tokenValue())
                .path("/")
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax")
                .maxAge(session.expiresIn())
                .build();
    }

    private static ResponseCookie csrfCookie(String value, Duration expiry) {
        return ResponseCookie.from(BrowserSessionCookieNames.CSRF, value)
                .path("/")
                .secure(true)
                .sameSite("Lax")
                .maxAge(expiry)
                .build();
    }

    private static String newCsrfToken() {
        var bytes = new byte[32];
        CSRF_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
