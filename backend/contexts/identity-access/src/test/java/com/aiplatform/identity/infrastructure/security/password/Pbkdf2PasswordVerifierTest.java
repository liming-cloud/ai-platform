package com.aiplatform.identity.infrastructure.security.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.PasswordCredential;
import com.aiplatform.identity.domain.PasswordHash;
import com.aiplatform.identity.domain.UserStatus;
import com.aiplatform.identity.domain.port.PasswordCredentialRepository;
import com.aiplatform.identity.domain.port.PasswordVerifier;
import com.aiplatform.shared.kernel.TenantId;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;

class Pbkdf2PasswordVerifierTest {
    @Test
    void matchesStoredCredentialHashForUser() {
        EnterpriseUser user = activeUser();
        PasswordCredentialRepository credentials = (tenantId, userId) -> Optional.of(new PasswordCredential(
                tenantId,
                userId,
                PasswordHash.of(encode("correct-password")),
                Instant.parse("2026-05-21T01:00:00Z")
        ));
        PasswordVerifier verifier = new Pbkdf2PasswordVerifier(credentials);

        boolean matched = verifier.matches(user, "correct-password");

        assertThat(matched).isTrue();
    }

    @Test
    void rejectsWrongPasswordWithoutLeakingCredential() {
        EnterpriseUser user = activeUser();
        PasswordCredentialRepository credentials = (tenantId, userId) -> Optional.of(new PasswordCredential(
                tenantId,
                userId,
                PasswordHash.of(encode("correct-password")),
                Instant.parse("2026-05-21T01:00:00Z")
        ));
        PasswordVerifier verifier = new Pbkdf2PasswordVerifier(credentials);

        boolean matched = verifier.matches(user, "wrong-password");

        assertThat(matched).isFalse();
    }

    @Test
    void rejectsWhenCredentialIsMissing() {
        CountingPbkdf2PasswordVerifier verifier = new CountingPbkdf2PasswordVerifier(
                (tenantId, userId) -> Optional.empty()
        );

        boolean matched = verifier.matches(activeUser(), "correct-password");

        assertThat(matched).isFalse();
        assertThat(verifier.dummyVerificationCount()).isEqualTo(1);
    }

    @Test
    void addsDummyWorkForLegacyLowIterationCredential() {
        EnterpriseUser user = activeUser();
        PasswordCredentialRepository credentials = (tenantId, userId) -> Optional.of(new PasswordCredential(
                tenantId,
                userId,
                PasswordHash.of(encode("correct-password")),
                Instant.parse("2026-05-21T01:00:00Z")
        ));
        CountingPbkdf2PasswordVerifier verifier = new CountingPbkdf2PasswordVerifier(credentials);

        boolean matched = verifier.matches(user, "correct-password");

        assertThat(matched).isTrue();
        assertThat(verifier.dummyVerificationCount()).isEqualTo(1);
    }

    @Test
    void performsDummyVerificationForUnknownAccountWithoutCredentialLookup() {
        PasswordCredentialRepository credentials = (tenantId, userId) -> {
            throw new AssertionError("未知账户不得查询真实密码凭证");
        };
        PasswordVerifier verifier = new Pbkdf2PasswordVerifier(credentials);

        assertThatCode(() -> verifier.performDummyVerification("candidate-password"))
                .doesNotThrowAnyException();
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

    private static String encode(String rawPassword) {
        byte[] salt = "test-salt-identity".getBytes(StandardCharsets.UTF_8);
        byte[] hash = pbkdf2(rawPassword, salt, 1000, 32);
        return "pbkdf2-sha256$1000$"
                + Base64.getEncoder().encodeToString(salt)
                + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations, int keyLengthBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, keyLengthBytes * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 算法不可用", exception);
        }
    }

    private static final class CountingPbkdf2PasswordVerifier extends Pbkdf2PasswordVerifier {
        private int dummyVerificationCount;

        private CountingPbkdf2PasswordVerifier(PasswordCredentialRepository credentialRepository) {
            super(credentialRepository);
        }

        @Override
        public void performDummyVerification(String rawPassword) {
            dummyVerificationCount++;
            super.performDummyVerification(rawPassword);
        }

        private int dummyVerificationCount() {
            return dummyVerificationCount;
        }
    }
}
