package com.aiplatform.shared.kernel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.regex.Pattern;

/** SHA-256 小写十六进制摘要的严格输入与常量时间比较工具。 */
public final class Sha256Digests {
    private static final Pattern LOWERCASE_SHA256 = Pattern.compile("[0-9a-f]{64}");

    private Sha256Digests() {
    }

    public static String requireLowercase(String value, String fieldName) {
        String requiredFieldName = Objects.requireNonNull(fieldName, "字段名不能为空").trim();
        if (requiredFieldName.isEmpty()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        if (value == null || !LOWERCASE_SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(requiredFieldName + "必须是 64 位小写 SHA-256");
        }
        return value;
    }

    public static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null
                || !LOWERCASE_SHA256.matcher(left).matches()
                || !LOWERCASE_SHA256.matcher(right).matches()) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
