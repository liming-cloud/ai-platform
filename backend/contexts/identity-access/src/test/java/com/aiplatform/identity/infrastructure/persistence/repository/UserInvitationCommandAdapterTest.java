package com.aiplatform.identity.infrastructure.persistence.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.application.command.InviteTenantUserCommand;
import com.aiplatform.identity.infrastructure.notification.InvitationNotificationPayloadCipher;
import com.aiplatform.identity.infrastructure.persistence.entity.IamUserInvitationPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserInvitationCommandAdapterTest {
    @Test
    void persistsOnlyEncryptedInvitationNotificationPayload() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        InvitationNotificationPayloadCipher cipher = mock(InvitationNotificationPayloadCipher.class);
        UserInvitationCommandAdapter adapter = new UserInvitationCommandAdapter(mapper, cipher);
        InviteTenantUserCommand command = new InviteTenantUserCommand(
                TenantId.of("2001"), "1002", "trace-1", "new-user", "新用户", "user@example.com", "3003"
        );
        String activationUrl = "https://console.example.com/activate?token=plain-bearer-token";
        when(mapper.countInvitationAssignableRole(2001L, 3003L)).thenReturn(1);
        when(mapper.countTenantUserByUsernameOrEmail(2001L, "new-user", "user@example.com")).thenReturn(0);
        when(mapper.insertPendingActivationUser(eq(2001L), eq("new-user"), eq("新用户"), eq("user@example.com"), any(), eq(1002L)))
                .thenReturn(9001L);
        when(cipher.encrypt("2001", "invite-1", activationUrl)).thenReturn("encrypted-envelope");

        adapter.createInvitation(command, "invite-1", "digest", activationUrl, Instant.parse("2026-07-19T12:00:00Z"));

        verify(mapper).insertInvitationNotification("invite-1", 2001L, "user@example.com", "encrypted-envelope");
    }

    @Test
    void reissueSupersedesOldCredentialBeforeQueuingNewCiphertext() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        InvitationNotificationPayloadCipher cipher = mock(InvitationNotificationPayloadCipher.class);
        UserInvitationCommandAdapter adapter = new UserInvitationCommandAdapter(mapper, cipher);
        var invitation = new com.aiplatform.identity.infrastructure.persistence.entity.IamUserInvitationOverviewPersistenceEntity();
        invitation.setInvitationId("invite-1");
        invitation.setUserId(9001L);
        invitation.setEmail("user@example.com");
        when(mapper.findUserInvitation(2001L, "invite-1")).thenReturn(invitation);
        when(mapper.reissueUserInvitation(eq(2001L), eq("invite-1"), eq("digest"), any())).thenReturn(1);
        when(cipher.encrypt(eq("2001"), eq("invite-1"), any())).thenReturn("new-envelope");

        adapter.reissue(TenantId.of("2001"), "invite-1", "digest", "https://console/activate?token=new", Instant.parse("2026-07-19T12:00:00Z"));

        verify(mapper).clearInvitationNotificationPayloads(2001L, "invite-1", "SUPERSEDED");
        verify(mapper).insertInvitationNotification("invite-1", 2001L, "user@example.com", "new-envelope");
    }

    @Test
    void activationDestroysEveryUndeliveredBearerPayload() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        UserInvitationCommandAdapter adapter = new UserInvitationCommandAdapter(
                mapper,
                mock(InvitationNotificationPayloadCipher.class)
        );
        IamUserInvitationPersistenceEntity invitation = new IamUserInvitationPersistenceEntity();
        invitation.setId("invite-1");
        invitation.setTenantId(2001L);
        invitation.setUserId(9001L);
        invitation.setExpiresAt(Instant.parse("2026-07-20T12:00:00Z"));
        when(mapper.findPendingUserInvitationByDigest("digest")).thenReturn(invitation);
        when(mapper.activateInvitedUser(eq(2001L), eq(9001L), any())).thenReturn(1);
        when(mapper.acceptUserInvitation(eq("invite-1"), any())).thenReturn(1);

        adapter.activate("digest", "password-hash", Instant.parse("2026-07-19T12:00:00Z"));

        verify(mapper).clearInvitationNotificationPayloads(2001L, "invite-1", "ACTIVATED");
    }

    @Test
    void revocationDestroysEveryUndeliveredBearerPayload() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        UserInvitationCommandAdapter adapter = new UserInvitationCommandAdapter(
                mapper,
                mock(InvitationNotificationPayloadCipher.class)
        );
        var invitation = new com.aiplatform.identity.infrastructure.persistence.entity.IamUserInvitationOverviewPersistenceEntity();
        invitation.setInvitationId("invite-1");
        invitation.setUserId(9001L);
        when(mapper.findUserInvitation(2001L, "invite-1")).thenReturn(invitation);
        when(mapper.revokeUserInvitation(2001L, "invite-1")).thenReturn(1);
        when(mapper.disablePendingInvitationUser(2001L, 9001L, 1002L)).thenReturn(1);

        adapter.revoke(TenantId.of("2001"), "invite-1", "1002");

        verify(mapper).clearInvitationNotificationPayloads(2001L, "invite-1", "CANCELLED");
    }
}
