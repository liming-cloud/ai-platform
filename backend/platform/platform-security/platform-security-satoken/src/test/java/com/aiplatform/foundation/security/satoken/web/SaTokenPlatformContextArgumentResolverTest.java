package com.aiplatform.foundation.security.satoken.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.PlatformPermissions;
import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.session.PlatformSessionGateway;
import com.aiplatform.foundation.security.web.BrowserSessionCookieNames;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class SaTokenPlatformContextArgumentResolverTest {

    @Test
    void 应只从Bearer令牌恢复平台上下文并忽略伪造范围字段() {
        var sessions = mock(PlatformSessionGateway.class);
        var tokenContext = tokenContext();
        when(sessions.resolve("platform-token")).thenReturn(tokenContext);
        var resolver = new SaTokenPlatformContextArgumentResolver(sessions, () -> 123L);
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer platform-token");
        request.addHeader("X-Tenant-Id", "100000000000000001");
        request.addHeader("X-Membership-Id", "3382738893456539650");
        request.addParameter("tenantId", "100000000000000001");
        request.addParameter("membershipId", "3382738893456539650");

        var actual = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

        assertEquals(new PlatformRequestContext(
                tokenContext.subjectId(),
                tokenContext.credentialType(),
                tokenContext.channel(),
                tokenContext.permissionVersion(),
                tokenContext.permissions(),
                "trc_123"), actual);
        verify(sessions).resolve("platform-token");
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void 应从平台浏览器会话Cookie恢复平台上下文且拒绝租户Cookie() {
        var sessions = mock(PlatformSessionGateway.class);
        var tokenContext = tokenContext();
        when(sessions.resolve("platform-browser-token")).thenReturn(tokenContext);
        var resolver = new SaTokenPlatformContextArgumentResolver(sessions, () -> 123L);
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(BrowserSessionCookieNames.PLATFORM_SESSION, "platform-browser-token"));

        var actual = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

        assertEquals(new PlatformRequestContext(
                tokenContext.subjectId(),
                tokenContext.credentialType(),
                tokenContext.channel(),
                tokenContext.permissionVersion(),
                tokenContext.permissions(),
                "trc_123"), actual);
        verify(sessions).resolve("platform-browser-token");
        verifyNoMoreInteractions(sessions);

        var tenantCookieRequest = new MockHttpServletRequest();
        tenantCookieRequest.setCookies(new Cookie(BrowserSessionCookieNames.TENANT_SESSION, "tenant-browser-token"));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException.class,
                () -> resolver.resolveArgument(null, null, new ServletWebRequest(tenantCookieRequest), null));
        verifyNoMoreInteractions(sessions);
    }

    private static PlatformRequestContext tokenContext() {
        return new PlatformRequestContext(
                new SubjectId(4921825928971026434L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                3L,
                Set.of(PlatformPermissions.TENANT_GOVERNANCE_WRITE),
                "trc_session");
    }
}
