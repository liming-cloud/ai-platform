package com.aiplatform.identity.infrastructure.security.password;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.PasswordCredential;
import com.aiplatform.identity.domain.port.PasswordCredentialRepository;
import com.aiplatform.identity.domain.port.PasswordVerifier;
import com.aiplatform.identity.infrastructure.security.password.model.StoredPasswordHash;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

/**
 * PBKDF2 密码校验适配器，哈希格式为 `pbkdf2-sha256$iterations$saltBase64$hashBase64`。
 */
@Component
public class Pbkdf2PasswordVerifier implements PasswordVerifier {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String STORAGE_ALGORITHM = "pbkdf2-sha256";
    private static final int EXPECTED_PARTS = 4;
    private static final int DUMMY_ITERATIONS = 120_000;
    private static final byte[] DUMMY_SALT = "aip-login-dummy!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DUMMY_EXPECTED_HASH = new byte[32];

    private final PasswordCredentialRepository credentialRepository;

    public Pbkdf2PasswordVerifier(PasswordCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    @Override
    public boolean matches(EnterpriseUser user, String rawPassword) {
        Optional<PasswordCredential> credential = credentialRepository.findByUserId(user.tenantId(), user.userId());
        if (credential.isEmpty()) {
            performDummyVerification(rawPassword);
            return false;
        }
        return matches(credential.orElseThrow(), rawPassword);
    }

    @Override
    public void performDummyVerification(String rawPassword) {
        byte[] actualHash = pbkdf2(
                rawPassword,
                DUMMY_SALT,
                DUMMY_ITERATIONS,
                DUMMY_EXPECTED_HASH.length
        );
        MessageDigest.isEqual(DUMMY_EXPECTED_HASH, actualHash);
        Arrays.fill(actualHash, (byte) 0);
    }

    private boolean matches(PasswordCredential credential, String rawPassword) {
        Optional<StoredPasswordHash> parsedHash = parseStoredHash(credential.passwordHash().value());
        if (parsedHash.isEmpty()) {
            performDummyVerification(rawPassword);
            return false;
        }
        StoredPasswordHash storedHash = parsedHash.orElseThrow();
        byte[] actualHash = pbkdf2(
                rawPassword,
                storedHash.salt(),
                storedHash.iterations(),
                storedHash.expectedHash().length
        );
        try {
            boolean matches = MessageDigest.isEqual(storedHash.expectedHash(), actualHash);
            if (storedHash.iterations() < DUMMY_ITERATIONS) {
                performDummyVerification(rawPassword);
            }
            return matches;
        } finally {
            Arrays.fill(actualHash, (byte) 0);
        }
    }

    private static Optional<StoredPasswordHash> parseStoredHash(String value) {
        String[] parts = value.split("\\$", -1);
        if (parts.length != EXPECTED_PARTS || !STORAGE_ALGORITHM.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            if (iterations <= 0 || salt.length == 0 || expectedHash.length == 0) {
                return Optional.empty();
            }
            return Optional.of(new StoredPasswordHash(iterations, salt, expectedHash));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations, int keyLengthBytes) {
        char[] passwordChars = rawPassword.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwordChars, salt, iterations, keyLengthBytes * 8);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(ALGORITHM + " 算法不可用", exception);
        } finally {
            spec.clearPassword();
            Arrays.fill(passwordChars, '\0');
        }
    }
}
