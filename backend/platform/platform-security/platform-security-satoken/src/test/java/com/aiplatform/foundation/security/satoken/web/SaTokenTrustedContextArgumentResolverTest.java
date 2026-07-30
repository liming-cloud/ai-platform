package com.aiplatform.foundation.security.satoken.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.foundation.security.identity.AccessChannel;
import com.aiplatform.foundation.security.identity.CredentialType;
import com.aiplatform.foundation.security.identity.MembershipId;
import com.aiplatform.foundation.security.identity.PermissionCode;
import com.aiplatform.foundation.security.identity.SubjectId;
import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.security.session.AuthenticatedSessionGateway;
import com.aiplatform.foundation.security.session.InvalidAuthenticatedSessionException;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class SaTokenTrustedContextArgumentResolverTest {

    @Test
    void 应只使用Bearer令牌恢复可信登录状态并忽略伪造参数和Header() {
        var sessions = mock(AuthenticatedSessionGateway.class);
        var tokenContext = tokenContext();
        when(sessions.resolve("signed-session-token")).thenReturn(tokenContext);
        var resolver = new SaTokenTrustedContextArgumentResolver(sessions, () -> 123L);
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer signed-session-token");
        request.addHeader("X-Tenant-Id", "100000000000000099");
        request.addHeader("X-Subject-Id", "100000000000000098");
        request.addHeader("X-Membership-Id", "100000000000000097");
        request.addHeader("X-Permissions", "platform:admin");
        request.addParameter("tenantId", "100000000000000099");
        request.addParameter("subjectId", "100000000000000098");
        request.addParameter("membershipId", "100000000000000097");
        request.addParameter("permissionVersion", "999");
        request.addParameter("channel", "PLATFORM");

        var actual = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

        assertEquals(new TrustedRequestContext(
                tokenContext.tenantId(),
                tokenContext.subjectId(),
                tokenContext.membershipId(),
                tokenContext.credentialType(),
                tokenContext.channel(),
                tokenContext.permissionVersion(),
                tokenContext.permissions(),
                "trc_123"), actual);
        verify(sessions).resolve("signed-session-token");
        verifyNoMoreInteractions(sessions);
    }

    @Test
    void 缺少Bearer令牌时不得回退到伪造登录状态Header() {
        var sessions = mock(AuthenticatedSessionGateway.class);
        var resolver = new SaTokenTrustedContextArgumentResolver(sessions, () -> 123L);
        var request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "100000000000000099");
        request.addHeader("X-Subject-Id", "100000000000000098");
        request.addHeader("X-Membership-Id", "100000000000000097");
        request.addHeader("X-Permissions", "platform:admin");

        assertThrows(
                InvalidAuthenticatedSessionException.class,
                () -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null));

        verifyNoInteractions(sessions);
    }

    private static TrustedRequestContext tokenContext() {
        return new TrustedRequestContext(
                new TenantId(100000000000000001L),
                new SubjectId(4921825928971026434L),
                new MembershipId(3382738893456539650L),
                CredentialType.PERSON,
                AccessChannel.CONSOLE,
                3,
                Set.of(new PermissionCode("knowledge:read"), new PermissionCode("knowledge:write")),
                "trc_session");
    }
}
