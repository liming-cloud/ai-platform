package com.aiplatform.foundation.organizationaccess.infrastructure.password;

import com.aiplatform.foundation.organizationaccess.application.authentication.port.out.PasswordVerifier;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 使用 JDK PBKDF2-HMAC-SHA256 的密码摘要适配器。
 */
public final class Pbkdf2PasswordAdapter implements PasswordVerifier {

    private static final String ALGORITHM_ID = "pbkdf2-sha256";
    private static final String FORMAT_VERSION = "v1";
    private static final String JDK_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int MAX_ITERATIONS = 2_000_000;
    private static final int SALT_BYTES = 16;
    private static final int DERIVED_KEY_BYTES = 32;
    private static final int MAX_ENCODED_LENGTH = 256;
    private static final int MAX_PASSWORD_LENGTH = 1024;

    private final SecureRandom secureRandom;

    public Pbkdf2PasswordAdapter(SecureRandom secureRandom) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "安全随机数生成器不能为空");
    }

    /**
     * 为新密码生成带独立随机盐的版本化摘要。
     */
    public String encode(char[] password) {
        requirePassword(password);
        var salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        var derived = derive(password, salt, ITERATIONS);
        try {
            return String.join(
                    "$",
                    ALGORITHM_ID,
                    FORMAT_VERSION,
                    Integer.toString(ITERATIONS),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(derived));
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(derived, (byte) 0);
        }
    }

    @Override
    public boolean matches(char[] candidate, String encodedPassword) {
        if (!validPassword(candidate)
                || encodedPassword == null
                || encodedPassword.length() > MAX_ENCODED_LENGTH) {
            return false;
        }
        try {
            var parts = encodedPassword.split("\\$", -1);
            if (parts.length != 5
                    || !ALGORITHM_ID.equals(parts[0])
                    || !FORMAT_VERSION.equals(parts[1])) {
                return false;
            }
            var iterations = Integer.parseInt(parts[2]);
            if (iterations < ITERATIONS || iterations > MAX_ITERATIONS) {
                return false;
            }
            var salt = Base64.getUrlDecoder().decode(parts[3]);
            var expected = Base64.getUrlDecoder().decode(parts[4]);
            if (salt.length != SALT_BYTES || expected.length != DERIVED_KEY_BYTES) {
                Arrays.fill(salt, (byte) 0);
                Arrays.fill(expected, (byte) 0);
                return false;
            }
            var actual = derive(candidate, salt, iterations);
            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(salt, (byte) 0);
                Arrays.fill(expected, (byte) 0);
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        var specification = new PBEKeySpec(password, salt, iterations, DERIVED_KEY_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(JDK_ALGORITHM).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("当前 JDK 不支持批准的密码摘要算法", exception);
        } finally {
            specification.clearPassword();
        }
    }

    private static void requirePassword(char[] password) {
        Objects.requireNonNull(password, "密码不能为空");
        if (!validPassword(password)) {
            throw new IllegalArgumentException("密码长度不正确");
        }
    }

    private static boolean validPassword(char[] password) {
        return password != null
                && password.length > 0
                && password.length <= MAX_PASSWORD_LENGTH;
    }
}
