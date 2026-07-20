package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.command.LoginCommand;
import com.aiplatform.identity.domain.EnterpriseUser;
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

class LoginAttemptProtectionApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-21T03:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void rejectsPreBlockedIdentityBeforeLookingUpAccount() {
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwords = mock(PasswordVerifier.class);
        AccessTokenIssuer tokens = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = mock(LoginAttemptProtection.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(protection.inspect(TenantId.of("2001"), "admin"))
                .thenReturn(LoginAttemptDecision.RATE_LIMITED);
        LoginApplicationService service = service(users, passwords, tokens, protection, audits);

        assertThatThrownBy(() -> service.login(command("admin", "password")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-LOGINRATELIMITED")
                .hasMessageNotContaining("admin");

        verifyNoInteractions(users, passwords, tokens);
        assertAuditResult(audits, "RATE_LIMITED");
    }

    @Test
    void thresholdFailureReturnsStableRateLimitContract() {
        EnterpriseUser user = activeUser();
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwords = mock(PasswordVerifier.class);
        AccessTokenIssuer tokens = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = mock(LoginAttemptProtection.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(protection.inspect(TenantId.of("2001"), "admin")).thenReturn(LoginAttemptDecision.ALLOWED);
        when(users.findByLoginName(TenantId.of("2001"), "admin")).thenReturn(Optional.of(user));
        when(passwords.matches(user, "wrong-password")).thenReturn(false);
        when(protection.recordFailure(TenantId.of("2001"), "admin"))
                .thenReturn(LoginAttemptDecision.RATE_LIMITED);
        LoginApplicationService service = service(users, passwords, tokens, protection, audits);

        assertThatThrownBy(() -> service.login(command("admin", "wrong-password")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-LOGINRATELIMITED");

        verify(tokens, never()).issue(user, NOW);
        assertAuditResult(audits, "RATE_LIMITED");
    }

    @Test
    void failsClosedBeforeLookupWhenProtectionStoreIsUnavailable() {
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwords = mock(PasswordVerifier.class);
        AccessTokenIssuer tokens = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = mock(LoginAttemptProtection.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(protection.inspect(TenantId.of("2001"), "admin"))
                .thenReturn(LoginAttemptDecision.UNAVAILABLE);
        LoginApplicationService service = service(users, passwords, tokens, protection, audits);

        assertThatThrownBy(() -> service.login(command("admin", "password")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-AUTHPROTECTIONUNAVAILABLE");

        verifyNoInteractions(users, passwords, tokens);
        assertAuditResult(audits, "PROTECTION_UNAVAILABLE");
    }

    @Test
    void failsClosedWhenFailureCannotBeRecorded() {
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwords = mock(PasswordVerifier.class);
        AccessTokenIssuer tokens = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = mock(LoginAttemptProtection.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(protection.inspect(TenantId.of("2001"), "missing-user"))
                .thenReturn(LoginAttemptDecision.ALLOWED);
        when(users.findByLoginName(TenantId.of("2001"), "missing-user")).thenReturn(Optional.empty());
        when(protection.recordFailure(TenantId.of("2001"), "missing-user"))
                .thenReturn(LoginAttemptDecision.UNAVAILABLE);
        LoginApplicationService service = service(users, passwords, tokens, protection, audits);

        assertThatThrownBy(() -> service.login(command("missing-user", "password")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-AUTHPROTECTIONUNAVAILABLE")
                .hasMessageNotContaining("missing-user");

        verify(passwords).performDummyVerification("password");
        verifyNoInteractions(tokens);
        assertAuditResult(audits, "PROTECTION_UNAVAILABLE");
    }

    @Test
    void successfulCredentialsMustResetProtectionBeforeTokenIssue() {
        EnterpriseUser user = activeUser();
        EnterpriseUserRepository users = mock(EnterpriseUserRepository.class);
        PasswordVerifier passwords = mock(PasswordVerifier.class);
        AccessTokenIssuer tokens = mock(AccessTokenIssuer.class);
        LoginAttemptProtection protection = mock(LoginAttemptProtection.class);
        AuditEventPublisher audits = mock(AuditEventPublisher.class);
        when(protection.inspect(TenantId.of("2001"), "admin")).thenReturn(LoginAttemptDecision.ALLOWED);
        when(users.findByLoginName(TenantId.of("2001"), "admin")).thenReturn(Optional.of(user));
        when(passwords.matches(user, "correct-password")).thenReturn(true);
        when(protection.reset(TenantId.of("2001"), "admin")).thenReturn(LoginAttemptDecision.UNAVAILABLE);
        LoginApplicationService service = service(users, passwords, tokens, protection, audits);

        assertThatThrownBy(() -> service.login(command("admin", "correct-password")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("AIP-IAM-AUTHPROTECTIONUNAVAILABLE");

        verify(tokens, never()).issue(user, NOW);
        assertAuditResult(audits, "PROTECTION_UNAVAILABLE");
    }

    private static LoginApplicationService service(
            EnterpriseUserRepository users,
            PasswordVerifier passwords,
            AccessTokenIssuer tokens,
            LoginAttemptProtection protection,
            AuditEventPublisher audits
    ) {
        return new LoginApplicationService(users, passwords, tokens, protection, audits, FIXED_CLOCK);
    }

    private static LoginCommand command(String loginName, String password) {
        return new LoginCommand(TenantId.of("2001"), loginName, password, "TRACE-LOGIN-PROTECTION");
    }

    private static EnterpriseUser activeUser() {
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

    private static void assertAuditResult(AuditEventPublisher audits, String result) {
        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audits).publish(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.action()).isEqualTo("LOGIN_FAILED");
        assertThat(event.detailAttributes()).containsEntry("result", result);
        assertThat(event.detailAttributes().values())
                .doesNotContain("admin", "password", "wrong-password", "correct-password");
    }
}
