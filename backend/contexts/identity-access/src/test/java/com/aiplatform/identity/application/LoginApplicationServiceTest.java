package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.command.LoginCommand;
import com.aiplatform.identity.application.result.LoginResult;
import com.aiplatform.identity.domain.AccessToken;
import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.LoginSession;
import com.aiplatform.identity.domain.UserStatus;
import com.aiplatform.identity.domain.port.AccessTokenIssuer;
import com.aiplatform.identity.domain.port.EnterpriseUserRepository;
import com.aiplatform.identity.domain.port.LoginAttemptDecision;
import com.aiplatform.identity.domain.port.LoginAttemptProtection;
import com.aiplatform.identity.domain.port.PasswordVerifier;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.ApplicationException;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.stereotype.Service;

class LoginApplicationServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-21T03:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void loginApplicationServiceIsSpringServiceBean() {
        assertThat(LoginApplicationService.class.getAnnotation(Service.class)).isNotNull();
    }

    @Test
    void loginCreatesSessionAndPublishesAuditEventWhenCredentialIsValid() {
        EnterpriseUser user = activeUser();
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwordVerifier = mock(PasswordVerifier.class);
        AccessTokenIssuer tokenIssuer = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = allowedProtection();
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(users.findByLoginName(TenantId.of("2001"), "admin")).thenReturn(Optional.of(user));
        when(passwordVerifier.matches(user, "correct-password")).thenReturn(true);
        when(tokenIssuer.issue(user, Instant.parse("2026-05-21T03:00:00Z"))).thenReturn(issuedSession(user));
        LoginApplicationService service = new LoginApplicationService(
                users,
                passwordVerifier,
                tokenIssuer,
                protection,
                audits,
                FIXED_CLOCK
        );

        LoginResult result = service.login(new LoginCommand(
                TenantId.of("2001"),
                "admin",
                "correct-password",
                "TRACE-LOGIN-001"
        ));

        assertThat(result.tenantId()).isEqualTo(TenantId.of("2001"));
        assertThat(result.userId()).isEqualTo("1001");
        assertThat(result.displayName()).isEqualTo("平台管理员");
        assertThat(result.roleCodes()).containsExactlyInAnyOrder("platform_admin", "auditor");
        assertThat(result.accessToken()).isEqualTo("issued-token-1001");
        assertThat(result.accessTokenExpiresAt()).isEqualTo(Instant.parse("2026-05-21T04:00:00Z"));
        verify(tokenIssuer).issue(user, Instant.parse("2026-05-21T03:00:00Z"));
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("LOGIN_SUCCESS");
        assertThat(eventCaptor.getValue().traceId()).isEqualTo("TRACE-LOGIN-001");
        assertThat(eventCaptor.getValue().detailAttributes()).containsEntry("result", "SUCCESS");
        assertThat(eventCaptor.getValue().detailAttributes().values())
                .doesNotContain("admin", "correct-password", "issued-token-1001");
        verify(protection).reset(TenantId.of("2001"), "admin");
    }

    @Test
    void loginRejectsInactiveUserWithoutCreatingSession() {
        EnterpriseUser inactiveUser = new EnterpriseUser(
                TenantId.of("2001"),
                "1002",
                "disabled",
                "停用用户",
                Set.of("business_user"),
                UserStatus.DISABLED,
                Instant.parse("2026-05-21T00:00:00Z"),
                Instant.parse("2026-05-21T01:00:00Z")
        );
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwordVerifier = mock(PasswordVerifier.class);
        AccessTokenIssuer tokenIssuer = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = allowedProtection();
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(users.findByLoginName(TenantId.of("2001"), "disabled")).thenReturn(Optional.of(inactiveUser));
        when(passwordVerifier.matches(inactiveUser, "correct-password")).thenReturn(true);
        LoginApplicationService service = new LoginApplicationService(
                users,
                passwordVerifier,
                tokenIssuer,
                protection,
                audits,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.login(new LoginCommand(
                TenantId.of("2001"),
                "disabled",
                "correct-password",
                "TRACE-LOGIN-002"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-AUTHFAILED")
                .hasMessageNotContaining("disabled");
        verify(passwordVerifier).matches(inactiveUser, "correct-password");
        verifyNoInteractions(tokenIssuer);
        verify(protection).recordFailure(TenantId.of("2001"), "disabled");
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("LOGIN_FAILED");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("1002");
        assertThat(eventCaptor.getValue().riskLevel()).isEqualTo("MEDIUM");
        assertThat(eventCaptor.getValue().detailAttributes()).containsEntry("result", "INVALID_CREDENTIALS");
    }

    @Test
    void loginRejectsInvalidPasswordWithoutCreatingSession() {
        EnterpriseUser user = activeUser();
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwordVerifier = mock(PasswordVerifier.class);
        AccessTokenIssuer tokenIssuer = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = allowedProtection();
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(users.findByLoginName(TenantId.of("2001"), "admin")).thenReturn(Optional.of(user));
        when(passwordVerifier.matches(user, "wrong-password")).thenReturn(false);
        LoginApplicationService service = new LoginApplicationService(
                users,
                passwordVerifier,
                tokenIssuer,
                protection,
                audits,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.login(new LoginCommand(
                TenantId.of("2001"),
                "admin",
                "wrong-password",
                "TRACE-LOGIN-003"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-AUTHFAILED")
                .hasMessageContaining("登录凭据无效")
                .hasMessageNotContaining("admin");
        verify(tokenIssuer, never()).issue(user, Instant.parse("2026-05-21T03:00:00Z"));
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("LOGIN_FAILED");
        assertThat(eventCaptor.getValue().actorId()).isEqualTo("1001");
        assertThat(eventCaptor.getValue().traceId()).isEqualTo("TRACE-LOGIN-003");
        assertThat(eventCaptor.getValue().detailAttributes()).containsEntry("result", "INVALID_CREDENTIALS");
        assertThat(eventCaptor.getValue().detailAttributes().values())
                .doesNotContain("admin", "wrong-password");
        verify(protection).recordFailure(TenantId.of("2001"), "admin");
    }

    @Test
    void loginRejectsUnknownUserWithAuditButWithoutAccountDetail() {
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwordVerifier = mock(PasswordVerifier.class);
        AccessTokenIssuer tokenIssuer = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = allowedProtection();
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(users.findByLoginName(TenantId.of("2001"), "missing-user")).thenReturn(Optional.empty());
        LoginApplicationService service = new LoginApplicationService(
                users,
                passwordVerifier,
                tokenIssuer,
                protection,
                audits,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.login(new LoginCommand(
                TenantId.of("2001"),
                "missing-user",
                "correct-password",
                "TRACE-LOGIN-004"
        )))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-AUTHFAILED")
                .hasMessageContaining("登录凭据无效")
                .hasMessageNotContaining("missing-user");
        verifyNoInteractions(tokenIssuer);
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo("LOGIN_FAILED");
        assertThat(eventCaptor.getValue().actorId()).isNull();
        assertThat(eventCaptor.getValue().resourceId()).isNull();
        assertThat(eventCaptor.getValue().detailAttributes()).containsEntry("result", "INVALID_CREDENTIALS");
        verify(passwordVerifier).performDummyVerification("correct-password");
        verify(protection).recordFailure(TenantId.of("2001"), "missing-user");
    }

    private static LoginAttemptProtection allowedProtection() {
        LoginAttemptProtection protection = mock(LoginAttemptProtection.class);
        when(protection.inspect(any(TenantId.class), anyString())).thenReturn(LoginAttemptDecision.ALLOWED);
        when(protection.recordFailure(any(TenantId.class), anyString())).thenReturn(LoginAttemptDecision.ALLOWED);
        when(protection.reset(any(TenantId.class), anyString())).thenReturn(LoginAttemptDecision.ALLOWED);
        return protection;
    }

    private static EnterpriseUser activeUser() {
        return new EnterpriseUser(
                TenantId.of("2001"),
                "1001",
                "admin",
                "平台管理员",
                Set.of("platform_admin", "auditor"),
                UserStatus.ACTIVE,
                Instant.parse("2026-05-21T00:00:00Z"),
                Instant.parse("2026-05-21T01:00:00Z")
        );
    }

    private static LoginSession issuedSession(EnterpriseUser user) {
        Instant issuedAt = Instant.parse("2026-05-21T03:00:00Z");
        return new LoginSession(
                user.tenantId(),
                "session-" + user.userId(),
                user.userId(),
                new AccessToken("issued-token-" + user.userId(), issuedAt.plusSeconds(3600)),
                issuedAt,
                true
        );
    }
}
