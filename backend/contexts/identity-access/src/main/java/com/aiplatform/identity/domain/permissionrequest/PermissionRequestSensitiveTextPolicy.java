package com.aiplatform.identity.domain.permissionrequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 权限申请自由文本的安全边界：新输入失败关闭，历史存量只以隔离后的安全文本进入领域和响应。
 */
public final class PermissionRequestSensitiveTextPolicy {
    public static final String REDACTED = "[REDACTED]";

    private static final int MAX_TEXT_LENGTH = 4_096;
    private static final int MAX_DECODE_DEPTH = 2;
    private static final int MAX_CANONICAL_FORMS = 48;
    private static final int MAX_BASE64_TOKENS_PER_FORM = 12;
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(?:^|[\\s?&;,\\[{(])[\"']?(?:x[-_ ]?api[-_ ]?key|api[-_ ]?key|"
                    + "aws[-_ ]?secret[-_ ]?access[-_ ]?key|aws[-_ ]?access[-_ ]?key[-_ ]?id|"
                    + "access[-_ ]?key|access[-_ ]?token|refresh[-_ ]?token|client[-_ ]?secret|"
                    + "password|passwd|pwd|secret|token|credential|private[-_ ]?key|"
                    + "authorization|proxy[-_ ]?authorization|cookie|set[-_ ]?cookie)"
                    + "[\"']?\\s*(?:=|:|：)\\s*(?:\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'|[^\\s;,}\\]]+)"
    );
    private static final Pattern AUTHENTICATION_VALUE = Pattern.compile(
            "(?i)(?:^|\\s)(?:authorization\\s*[:=]\\s*)?(?:bearer|basic)\\s+[a-z0-9._~+/=-]{4,}"
    );
    private static final Pattern KNOWN_TOKEN_SHAPE = Pattern.compile(
            "(?i)(?<![a-z0-9_])(?:sk-[a-z0-9_-]{8,}|ghp_[a-z0-9]{8,}|"
                    + "github_pat_[a-z0-9_]{8,}|xox[baprs]-[a-z0-9-]{8,}|AKIA[A-Z0-9]{12,})(?![a-z0-9_])"
    );
    private static final Pattern JWT = Pattern.compile(
            "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}"
                    + "(?![A-Za-z0-9_-])"
    );
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?i)-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----"
    );
    private static final Pattern BASE64_TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9+/_-])[A-Za-z0-9+/_-]{6,2048}={0,2}(?![A-Za-z0-9+/_=-])"
    );

    private PermissionRequestSensitiveTextPolicy() {
    }

    public static String requireSafeNewText(String value, String fieldName) {
        String normalizedFieldName = requireFieldName(fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(normalizedFieldName + "不能为空");
        }
        String normalized = value.trim();
        if (containsSuspiciousSecret(normalized)) {
            throw new IllegalArgumentException(normalizedFieldName + "不能包含疑似认证凭据或秘密");
        }
        return normalized;
    }

    public static String sanitizeHistoricalText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim();
        return containsSuspiciousSecret(normalized) ? REDACTED : normalized;
    }

    public static PermissionRequest sanitizeHistorical(PermissionRequest request) {
        PermissionRequest source = Objects.requireNonNull(request, "权限申请不能为空");
        String safeReason = sanitizeHistoricalText(source.reason());
        String safeComment = sanitizeHistoricalText(source.decisionComment());
        if (Objects.equals(safeReason, source.reason()) && Objects.equals(safeComment, source.decisionComment())) {
            return source;
        }
        return new PermissionRequest(
                source.tenantId(),
                source.requestId(),
                source.requesterId(),
                source.resourceType(),
                source.resourceId(),
                source.permissionCode(),
                safeReason,
                source.status(),
                source.reviewerId(),
                safeComment,
                source.createdAt(),
                source.updatedAt()
        );
    }

    static boolean containsSuspiciousSecret(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.length() > MAX_TEXT_LENGTH) {
            return true;
        }
        return canonicalForms(value).stream().anyMatch(PermissionRequestSensitiveTextPolicy::matchesSecretGrammar);
    }

    private static boolean matchesSecretGrammar(String candidate) {
        return SECRET_ASSIGNMENT.matcher(candidate).find()
                || AUTHENTICATION_VALUE.matcher(candidate).find()
                || KNOWN_TOKEN_SHAPE.matcher(candidate).find()
                || JWT.matcher(candidate).find()
                || PRIVATE_KEY.matcher(candidate).find();
    }

    private static Set<String> canonicalForms(String value) {
        Set<String> forms = new LinkedHashSet<>();
        addCandidate(forms, value.trim());
        for (int depth = 0; depth < MAX_DECODE_DEPTH && forms.size() < MAX_CANONICAL_FORMS; depth++) {
            for (String candidate : new ArrayList<>(forms)) {
                addCandidate(forms, decodeUrlFormLenient(candidate));
                addDecodedBase64Tokens(forms, candidate);
                if (forms.size() >= MAX_CANONICAL_FORMS) {
                    break;
                }
            }
        }
        return forms;
    }

    private static void addDecodedBase64Tokens(Set<String> forms, String candidate) {
        Matcher matcher = BASE64_TOKEN.matcher(candidate);
        int decodedTokens = 0;
        while (matcher.find()
                && decodedTokens < MAX_BASE64_TOKENS_PER_FORM
                && forms.size() < MAX_CANONICAL_FORMS) {
            String encoded = matcher.group();
            addDecodedBase64(forms, encoded, Base64.getDecoder());
            addDecodedBase64(forms, encoded, Base64.getUrlDecoder());
            decodedTokens++;
        }
    }

    private static void addDecodedBase64(Set<String> forms, String encoded, Base64.Decoder decoder) {
        try {
            String padded = encoded + "=".repeat((4 - encoded.length() % 4) % 4);
            byte[] decodedBytes = decoder.decode(padded);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
            if (Arrays.equals(decoded.getBytes(StandardCharsets.UTF_8), decodedBytes)
                    && decoded.chars().noneMatch(PermissionRequestSensitiveTextPolicy::isUnsafeControlCharacter)) {
                addCandidate(forms, decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // 当前片段不是这种 Base64 grammar；其他 canonical form 仍会继续检查。
        }
    }

    private static boolean isUnsafeControlCharacter(int value) {
        return Character.isISOControl(value) && !Character.isWhitespace(value);
    }

    private static String decodeUrlFormLenient(String value) {
        if (!value.contains("%") && !value.contains("+")) {
            return value;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            StringBuilder decoded = new StringBuilder(value.length());
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (current == '+') {
                    decoded.append(' ');
                } else if (current == '%' && index + 2 < value.length()
                        && isHex(value.charAt(index + 1)) && isHex(value.charAt(index + 2))) {
                    decoded.append((char) Integer.parseInt(value.substring(index + 1, index + 3), 16));
                    index += 2;
                } else {
                    decoded.append(current);
                }
            }
            return decoded.toString();
        }
    }

    private static boolean isHex(char value) {
        return Character.digit(value, 16) >= 0;
    }

    private static void addCandidate(Set<String> forms, String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_TEXT_LENGTH
                || forms.size() >= MAX_CANONICAL_FORMS) {
            return;
        }
        forms.add(candidate.trim());
    }

    private static String requireFieldName(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("字段名不能为空");
        }
        return fieldName.trim();
    }
}
