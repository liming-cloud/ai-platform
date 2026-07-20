package com.aiplatform.identity.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InvitationNotificationPayloadCipherTest {
    private static final String KEY = "k".repeat(32);

    @Test
    void encryptsActivationUrlWithoutPersistingBearerCredential() {
        InvitationNotificationPayloadCipher cipher = cipher("active-key", KEY, "");
        String activationUrl = "https://console.example.com/activate?token=plain-bearer-token";

        String envelope = cipher.encrypt("2001", "invite-1", activationUrl);

        assertThat(envelope)
                .contains("aip-invitation-outbox-v1")
                .contains("active-key")
                .doesNotContain("plain-bearer-token", "activationUrl", "console.example.com");
        assertThat(cipher.decrypt("2001", "invite-1", envelope)).isEqualTo(activationUrl);
    }

    @Test
    void bindsCiphertextToTenantAndInvitationAndSupportsPreviousKeyRead() {
        InvitationNotificationPayloadCipher oldCipher = cipher("old-key", KEY, "");
        String envelope = oldCipher.encrypt("2001", "invite-1", "https://console.example.com/activate?token=secret");
        InvitationNotificationPayloadCipher rotated = cipher(
                "new-key",
                "abcdef0123456789abcdef0123456789",
                "old-key=" + KEY
        );

        assertThat(rotated.decrypt("2001", "invite-1", envelope)).contains("token=secret");
        assertThatThrownBy(() -> rotated.decrypt("2002", "invite-1", envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密文");
        assertThatThrownBy(() -> rotated.decrypt("2001", "invite-2", envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密文");
    }

    @Test
    void acceptsExactThirtyTwoByteBase64KeysAndRejectsWeakMaterial() {
        byte[] distinctKeyBytes = new byte[32];
        for (int index = 0; index < distinctKeyBytes.length; index++) {
            distinctKeyBytes[index] = (byte) (index + 1);
        }
        String encoded = "b64:" + Base64.getEncoder().encodeToString(distinctKeyBytes);
        InvitationNotificationPayloadCipher cipher = cipher("active-key", encoded, "");

        String envelope = cipher.encrypt("2001", "invite-1", "https://console.example.com/activate?token=secret");

        assertThat(cipher.decrypt("2001", "invite-1", envelope)).contains("token=secret");
        assertThatThrownBy(() -> cipher("active-key", "b64:" + Base64.getEncoder().encodeToString(new byte[32]), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
    }

    private static InvitationNotificationPayloadCipher cipher(String keyId, String key, String previousKeys) {
        return new InvitationNotificationPayloadCipher(
                JsonMapper.builder().build(),
                keyId,
                key,
                previousKeys,
                new SecureRandom()
        );
    }
}
