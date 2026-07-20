package com.aiplatform.identity.domain;

import com.aiplatform.shared.kernel.ValueObject;
import java.util.Base64;

/**
 * 密码哈希值对象，只保存不可逆哈希串，不承载明文密码。
 */
public record PasswordHash(String value) implements ValueObject {
    private static final String SUPPORTED_ALGORITHM = "pbkdf2-sha256";
    private static final int EXPECTED_PARTS = 4;

    public PasswordHash {
        if (value == null) {
            throw new IllegalArgumentException("密码哈希不能为空");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("密码哈希不能为空白");
        }
        validatePbkdf2Format(value);
    }

    private static void validatePbkdf2Format(String value) {
        String[] parts = value.split("\\$", -1);
        if (parts.length != EXPECTED_PARTS || !SUPPORTED_ALGORITHM.equals(parts[0])) {
            throw new IllegalArgumentException("密码哈希算法不受支持");
        }
        int iterations = parseIterations(parts[1]);
        byte[] salt = decodeBase64Part(parts[2], "salt");
        byte[] hash = decodeBase64Part(parts[3], "hash");
        if (iterations <= 0 || salt.length == 0 || hash.length == 0) {
            throw new IllegalArgumentException("密码哈希参数无效");
        }
    }

    private static int parseIterations(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("密码哈希迭代次数无效", exception);
        }
    }

    private static byte[] decodeBase64Part(String value, String partName) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("密码哈希 " + partName + " 无效", exception);
        }
    }

    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }
}
