package com.aiplatform.identity.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiplatform.identity.infrastructure.persistence.entity.IamNotificationOutboxPersistenceEntity;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class InvitationNotificationDispatcherTest {
    @Test
    void decryptsOnlyAfterClaimAndDeliveryDestroysCiphertext() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        InvitationNotificationPayloadCipher cipher = mock(InvitationNotificationPayloadCipher.class);
        IamNotificationOutboxPersistenceEntity row = notification();
        when(mapper.findQueuedInvitationNotifications(20)).thenReturn(List.of(row));
        when(mapper.claimInvitationNotification(11L)).thenReturn(1);
        when(mapper.markInvitationNotificationDelivered(11L)).thenReturn(1);
        when(cipher.decrypt("2001", "invite-1", "encrypted-envelope"))
                .thenReturn("https://console.example.com/activate?token=plain-bearer-token");
        InvitationNotificationDispatcher dispatcher = new InvitationNotificationDispatcher(
                mapper, sender, cipher, "no-reply@example.com"
        );

        dispatcher.dispatch();

        verify(mapper, never()).expireInvitationNotificationPayloads();
        verify(sender).send(any(SimpleMailMessage.class));
        verify(mapper).markInvitationNotificationDelivered(11L);
        verify(mapper, never()).markInvitationNotificationFailed(eq(11L), any());
    }

    @Test
    void failureRetainsCiphertextButNeverPersistsExceptionMessage() {
        IamAuthorizationMapper mapper = mock(IamAuthorizationMapper.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        InvitationNotificationPayloadCipher cipher = mock(InvitationNotificationPayloadCipher.class);
        IamNotificationOutboxPersistenceEntity row = notification();
        when(mapper.findQueuedInvitationNotifications(20)).thenReturn(List.of(row));
        when(mapper.claimInvitationNotification(11L)).thenReturn(1);
        when(cipher.decrypt("2001", "invite-1", "encrypted-envelope"))
                .thenThrow(new IllegalArgumentException("token=plain-bearer-token"));
        InvitationNotificationDispatcher dispatcher = new InvitationNotificationDispatcher(
                mapper, sender, cipher, "no-reply@example.com"
        );

        dispatcher.dispatch();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(mapper).markInvitationNotificationFailed(eq(11L), error.capture());
        assertThat(error.getValue())
                .contains("IllegalArgumentException")
                .doesNotContain("plain-bearer-token", "token=");
    }

    private static IamNotificationOutboxPersistenceEntity notification() {
        IamNotificationOutboxPersistenceEntity row = new IamNotificationOutboxPersistenceEntity();
        row.setId(11L);
        row.setTenantId(2001L);
        row.setAggregateId("invite-1");
        row.setRecipient("user@example.com");
        row.setPayloadJson("encrypted-envelope");
        return row;
    }
}
