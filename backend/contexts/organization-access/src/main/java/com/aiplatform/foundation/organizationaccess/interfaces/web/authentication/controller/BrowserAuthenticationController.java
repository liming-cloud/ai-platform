package com.aiplatform.foundation.organizationaccess.interfaces.web.authentication.controller;

import com.aiplatform.foundation.organizationaccess.application.authentication.command.BrowserLoginCommand;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionCapabilityResult;
import com.aiplatform.foundation.organizationaccess.application.authentication.dto.BrowserSessionIssue;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserAuthenticationService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionCapabilityService;
import com.aiplatform.foundation.organizationaccess.application.authentication.service.BrowserSessionTerminationService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final BrowserSessionCapabilityService capabilities;
    private final BrowserSessionTerminationService termination;
    private final EntityIdGenerator identifiers;

    public BrowserAuthenticationController(
            BrowserAuthenticationService authentication,
            BrowserSessionCapabilityService capabilities,
            BrowserSessionTerminationService termination,
            EntityIdGenerator identifiers) {
        this.authentication = authentication;
        this.capabilities = capabilities;
        this.termination = termination;
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
     * 返回当前浏览器已验证范围的最小导航能力清单。
     */
    @GetMapping("/browser-session")
    public ResponseEntity<?> session(
            @CookieValue(name = BrowserSessionCookieNames.TENANT_SESSION, required = false)
            String tenantSessionToken,
            @CookieValue(name = BrowserSessionCookieNames.PLATFORM_SESSION, required = false)
            String platformSessionToken) {
        var result = capabilities.inspect(tenantSessionToken, platformSessionToken);
        if (result.capability().isEmpty()) {
            var response = ResponseEntity.status(HttpStatus.UNAUTHORIZED);
            clearInvalidSessions(response, result);
            return response.build();
        }
        var response = ResponseEntity.ok();
        clearInvalidSessions(response, result);
        return response.body(result.capability().orElseThrow());
    }

    /**
     * 撤销当前浏览器登录族并清除两个范围 cookie 与 CSRF 防护值。
     */
    @DeleteMapping("/browser-session")
    public ResponseEntity<Void> logout(
            @CookieValue(name = BrowserSessionCookieNames.TENANT_SESSION, required = false)
            String tenantSessionToken,
            @CookieValue(name = BrowserSessionCookieNames.PLATFORM_SESSION, required = false)
            String platformSessionToken) {
        termination.terminate(tenantSessionToken, platformSessionToken);
        return clearedBrowserSessionFamily(ResponseEntity.noContent()).build();
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

    private static void clearInvalidSessions(
            ResponseEntity.HeadersBuilder<?> response,
            BrowserSessionCapabilityResult result) {
        if (result.clearTenantSession()) {
            response.header(HttpHeaders.SET_COOKIE, clearedSessionCookie(BrowserSessionCookieNames.TENANT_SESSION).toString());
        }
        if (result.clearPlatformSession()) {
            response.header(HttpHeaders.SET_COOKIE, clearedSessionCookie(BrowserSessionCookieNames.PLATFORM_SESSION).toString());
        }
        if (result.clearTenantSession() && result.clearPlatformSession()) {
            response.header(HttpHeaders.SET_COOKIE, csrfCookie("", Duration.ZERO).toString());
        }
    }

    private static ResponseEntity.HeadersBuilder<?> clearedBrowserSessionFamily(
            ResponseEntity.HeadersBuilder<?> response) {
        return response
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie(BrowserSessionCookieNames.TENANT_SESSION).toString())
                .header(HttpHeaders.SET_COOKIE, clearedSessionCookie(BrowserSessionCookieNames.PLATFORM_SESSION).toString())
                .header(HttpHeaders.SET_COOKIE, csrfCookie("", Duration.ZERO).toString());
    }

    private static ResponseCookie clearedSessionCookie(String name) {
        return ResponseCookie.from(name, "")
                .path("/")
                .secure(true)
                .httpOnly(true)
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
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
