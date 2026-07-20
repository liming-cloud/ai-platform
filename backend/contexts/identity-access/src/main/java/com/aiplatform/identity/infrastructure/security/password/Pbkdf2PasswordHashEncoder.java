package com.aiplatform.identity.infrastructure.security.password;

import com.aiplatform.identity.application.port.PasswordHashEncoder;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class Pbkdf2PasswordHashEncoder implements PasswordHashEncoder {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String encode(String rawPassword) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        char[] chars = rawPassword.toCharArray();
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)).getEncoded();
            return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 算法不可用", exception);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }
}
