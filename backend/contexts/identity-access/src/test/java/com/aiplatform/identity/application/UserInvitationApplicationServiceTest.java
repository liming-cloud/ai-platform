package com.aiplatform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.command.InviteTenantUserCommand;
import com.aiplatform.identity.application.port.PasswordHashEncoder;
import com.aiplatform.identity.application.port.UserInvitationCommandPort;
import com.aiplatform.identity.application.result.UserActivationResult;
import com.aiplatform.identity.application.result.UserInvitationResult;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class UserInvitationApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");
    private static final String SIGNING_KEY = "s".repeat(32);

    @Test
    void createsExpiringInvitationAndActivatesWithHashedPassword() {
        UserInvitationCommandPort commandPort = mock(UserInvitationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        PasswordHashEncoder passwordHashEncoder = mock(PasswordHashEncoder.class);
        UserInvitationApplicationService service = new UserInvitationApplicationService(
                commandPort,
                auditPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                passwordHashEncoder,
                SIGNING_KEY,
                "https://console.example.com/activate"
        );
        InviteTenantUserCommand command = new InviteTenantUserCommand(
                TenantId.of("2001"), "1002", "trace-invite", "new-user", "新用户", "user@example.com", "3003"
        );
        when(commandPort.createInvitation(eq(command), any(), any(), any(), eq(NOW.plusSeconds(86_400))))
                .thenAnswer(invocation -> new UserInvitationResult(
                        invocation.getArgument(1), "9001", "PENDING_ACTIVATION", "PENDING", invocation.getArgument(4)
                ));

        UserInvitationResult invitation = service.invite(command);

        ArgumentCaptor<String> digestCaptor = ArgumentCaptor.forClass(String.class);
        verify(commandPort).createInvitation(eq(command), eq(invitation.invitationId()), digestCaptor.capture(), any(), eq(NOW.plusSeconds(86_400)));
        assertThat(digestCaptor.getValue()).doesNotContain(invitation.invitationId());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCaptor.capture());
        assertThat(auditCaptor.getValue().resourceId()).isEqualTo("9001");
        assertThat(auditCaptor.getValue().detailAttributes()).containsEntry("invitationId", invitation.invitationId());

        when(passwordHashEncoder.encode("StrongPassword!2026")).thenReturn("encoded-password");
        when(commandPort.activate(any(), eq("encoded-password"), eq(NOW)))
                .thenReturn(new UserActivationResult(invitation.invitationId(), "9001", "2001", "ACTIVE"));

        UserActivationResult activated = service.activate(" token-digest ", "StrongPassword!2026");

        assertThat(activated.status()).isEqualTo("ACTIVE");
        ArgumentCaptor<String> activationDigestCaptor = ArgumentCaptor.forClass(String.class);
        verify(commandPort).activate(activationDigestCaptor.capture(), eq("encoded-password"), eq(NOW));
        assertThat(activationDigestCaptor.getValue()).isNotEqualTo("token-digest");
    }

    @Test
    void rejectsWeakActivationPasswordBeforePersistence() {
        UserInvitationApplicationService service = new UserInvitationApplicationService(
                mock(UserInvitationCommandPort.class),
                mock(AuditEventPublisher.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PasswordHashEncoder.class),
                SIGNING_KEY,
                "https://console.example.com/activate"
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.activate("token", "too-short"))
                .withMessageContaining("15 到 128");
    }

    @Test
    void reissuesAndRevokesPendingInvitationThroughAuditedCommands() {
        UserInvitationCommandPort commandPort = mock(UserInvitationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        UserInvitationApplicationService service = new UserInvitationApplicationService(
                commandPort, auditPublisher, Clock.fixed(NOW, ZoneOffset.UTC), mock(PasswordHashEncoder.class),
                SIGNING_KEY, "https://console.example.com/activate");
        when(commandPort.reissue(eq(TenantId.of("2001")), eq("invite-1"), any(), any(), eq(NOW.plusSeconds(86_400))))
                .thenReturn(new UserInvitationResult("invite-1", "9001", "PENDING_ACTIVATION", "QUEUED", NOW.plusSeconds(86_400)));

        UserInvitationResult result = service.reissue(TenantId.of("2001"), "invite-1", "1002");
        service.revoke(TenantId.of("2001"), "invite-1", "1002", "trace-revoke", "人员入职计划取消");

        assertThat(result.deliveryStatus()).isEqualTo("QUEUED");
        verify(commandPort).revoke(TenantId.of("2001"), "invite-1", "1002");
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher, org.mockito.Mockito.times(2)).publish(auditCaptor.capture());
        AuditEvent revokeAudit = auditCaptor.getAllValues().get(1);
        assertThat(revokeAudit.traceId()).isEqualTo("trace-revoke");
        assertThat(revokeAudit.detailAttributes()).containsEntry("reason", "人员入职计划取消");
    }

    @Test
    void rejectsMissingOrOversizedRevocationReasonBeforePersistence() {
        UserInvitationCommandPort commandPort = mock(UserInvitationCommandPort.class);
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        UserInvitationApplicationService service = new UserInvitationApplicationService(
                commandPort, auditPublisher, Clock.fixed(NOW, ZoneOffset.UTC), mock(PasswordHashEncoder.class),
                SIGNING_KEY, "https://console.example.com/activate");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.revoke(TenantId.of("2001"), "invite-1", "1002", "trace-revoke", "  "))
                .withMessage("撤销原因不能为空");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.revoke(TenantId.of("2001"), "invite-1", "1002", "trace-revoke", "x".repeat(501)))
                .withMessage("撤销原因不能超过 500 个字符");
        verifyNoInteractions(commandPort, auditPublisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "local-dev-invitation-key-change-before-prod",
            "replace-invitation-signing-key",
            "changeme-invitation-signing-key",
            "short-signing-key",
            " 0123456789abcdef0123456789abcdef"
    })
    void rejectsWeakOrPlaceholderInvitationSigningKeysAtConstruction(String signingKey) {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserInvitationApplicationService(
                mock(UserInvitationCommandPort.class),
                mock(AuditEventPublisher.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PasswordHashEncoder.class),
                signingKey,
                "https://console.example.com/activate"
        )).withMessageContaining("邀请签名密钥");
    }

    @Test
    void rejectsMissingInvitationSigningKeyAtConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new UserInvitationApplicationService(
                mock(UserInvitationCommandPort.class),
                mock(AuditEventPublisher.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PasswordHashEncoder.class),
                null,
                "https://console.example.com/activate"
        )).withMessageContaining("邀请签名密钥不能为空");
    }
}
