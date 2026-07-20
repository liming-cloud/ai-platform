package com.aiplatform.identity.infrastructure.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.LoginSession;
import com.aiplatform.identity.domain.UserStatus;
import com.aiplatform.identity.domain.port.AccessTokenIssuer;
import com.aiplatform.identity.domain.port.LoginSessionRepository;
import com.aiplatform.infra.satoken.gateway.SaTokenSessionGateway;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SaTokenLoginSessionAdapterTest {
    @Test
    void issueCreatesSaTokenSessionWithTenantScopedLoginId() {
        SaTokenSessionGateway gateway = mock(SaTokenSessionGateway.class);
        when(gateway.createLoginSession("2001:user:1001", 7200L)).thenReturn("token-2001-user-1001");
        SaTokenLoginSessionAdapter adapter = new SaTokenLoginSessionAdapter(gateway, Duration.ofHours(2));
        EnterpriseUser user = user();
        Instant issuedAt = Instant.parse("2026-05-21T05:00:00Z");

        LoginSession session = adapter.issue(user, issuedAt);

        assertThat(AccessTokenIssuer.class).isAssignableFrom(SaTokenLoginSessionAdapter.class);
        assertThat(LoginSessionRepository.class).isAssignableFrom(SaTokenLoginSessionAdapter.class);
        verify(gateway).createLoginSession("2001:user:1001", 7200L);
        assertThat(session.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(session.sessionId()).isEqualTo("token-2001-user-1001");
        assertThat(session.userId()).isEqualTo("1001");
        assertThat(session.accessToken().tokenValue()).isEqualTo("token-2001-user-1001");
        assertThat(session.accessToken().expiresAt()).isEqualTo(Instant.parse("2026-05-21T07:00:00Z"));
    }

    @Test
    void findActiveByTokenRejectsTokenFromDifferentTenant() {
        SaTokenSessionGateway gateway = mock(SaTokenSessionGateway.class);
        when(gateway.findLoginIdByToken("token-3001-1001")).thenReturn(Optional.of("3001:user:1001"));
        SaTokenLoginSessionAdapter adapter = new SaTokenLoginSessionAdapter(gateway, Duration.ofHours(1));

        Optional<LoginSession> session = adapter.findActiveByToken(TenantId.of("2001"), "token-3001-1001");

        assertThat(session).isEmpty();
        verify(gateway).findLoginIdByToken("token-3001-1001");
        verify(gateway, never()).getTokenTimeoutSeconds("token-3001-1001");
    }

    @Test
    void findActiveByTokenRestoresTenantScopedSession() {
        SaTokenSessionGateway gateway = mock(SaTokenSessionGateway.class);
        when(gateway.findLoginIdByToken("token-2001-1001")).thenReturn(Optional.of("2001:user:1001"));
        when(gateway.getTokenTimeoutSeconds("token-2001-1001")).thenReturn(3600L);
        SaTokenLoginSessionAdapter adapter = new SaTokenLoginSessionAdapter(gateway, Duration.ofHours(1));

        Optional<LoginSession> session = adapter.findActiveByToken(TenantId.of("2001"), "token-2001-1001");

        assertThat(session).isPresent();
        assertThat(session.orElseThrow().userId()).isEqualTo("1001");
        assertThat(session.orElseThrow().accessToken().tokenValue()).isEqualTo("token-2001-1001");
        verify(gateway).findLoginIdByToken("token-2001-1001");
        verify(gateway).getTokenTimeoutSeconds("token-2001-1001");
    }

    @Test
    void issueRejectsBlankTokenReturnedBySaToken() {
        SaTokenSessionGateway gateway = mock(SaTokenSessionGateway.class);
        when(gateway.createLoginSession("2001:user:1001", 7200L)).thenReturn(" ");
        SaTokenLoginSessionAdapter adapter = new SaTokenLoginSessionAdapter(gateway, Duration.ofHours(2));

        assertThatThrownBy(() -> adapter.issue(user(), Instant.parse("2026-05-21T05:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("访问令牌不能为空");
    }

    @Test
    void findActiveByTokenRejectsBlankTokenBeforeGateway() {
        SaTokenSessionGateway gateway = mock(SaTokenSessionGateway.class);
        SaTokenLoginSessionAdapter adapter = new SaTokenLoginSessionAdapter(gateway, Duration.ofHours(1));

        assertThatThrownBy(() -> adapter.findActiveByToken(TenantId.of("2001"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("访问令牌不能为空");
        verifyNoInteractions(gateway);
    }

    @Test
    void findActiveByTokenRejectsMalformedLoginIdInsteadOfEmptySession() {
        SaTokenSessionGateway gateway = mock(SaTokenSessionGateway.class);
        when(gateway.findLoginIdByToken("token-2001-1001")).thenReturn(Optional.of("malformed"));
        SaTokenLoginSessionAdapter adapter = new SaTokenLoginSessionAdapter(gateway, Duration.ofHours(1));

        assertThatThrownBy(() -> adapter.findActiveByToken(TenantId.of("2001"), "token-2001-1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体格式非法");
        verify(gateway, never()).getTokenTimeoutSeconds("token-2001-1001");
    }

    @Test
    void revokeByTokenLogsOutOnlyTenantScopedActiveSession() {
        SaTokenSessionGateway gateway = mock(SaTokenSessionGateway.class);
        when(gateway.findLoginIdByToken("token-2001-1001")).thenReturn(Optional.of("2001:user:1001"));
        when(gateway.getTokenTimeoutSeconds("token-2001-1001")).thenReturn(3600L);
        SaTokenLoginSessionAdapter adapter = new SaTokenLoginSessionAdapter(gateway, Duration.ofHours(1));

        adapter.revokeByToken(TenantId.of("2001"), "token-2001-1001");

        verify(gateway).revokeToken("token-2001-1001");
    }

    @Test
    void revokeByTokenRejectsBlankTokenBeforeGateway() {
        SaTokenSessionGateway gateway = mock(SaTokenSessionGateway.class);
        SaTokenLoginSessionAdapter adapter = new SaTokenLoginSessionAdapter(gateway, Duration.ofHours(1));

        assertThatThrownBy(() -> adapter.revokeByToken(TenantId.of("2001"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("访问令牌不能为空");
        verifyNoInteractions(gateway);
    }

    @Test
    void springUsesPublicConstructorForDependencyInjection() {
        boolean hasAutowiredConstructor = Arrays.stream(SaTokenLoginSessionAdapter.class.getConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Autowired.class));

        assertThat(hasAutowiredConstructor).isTrue();
    }

    private static EnterpriseUser user() {
        return new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin"),
                UserStatus.ACTIVE,
                Instant.parse("2026-05-21T00:00:00Z"),
                Instant.parse("2026-05-21T01:00:00Z")
        );
    }

}
