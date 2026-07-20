package com.aiplatform.infra.springai.model.provider;

import com.aiplatform.shared.kernel.SensitiveMaterialDetector;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts provider failures into bounded diagnostics that are safe to return or log.
 *
 * <p>The sanitizer deliberately owns transport decoding as well as secret grammar matching. Callers must not
 * inspect or log the raw provider exception before invoking this seam.</p>
 */
public final class ProviderErrorSanitizer {
    static final int MAX_DIAGNOSTIC_LENGTH = 180;
    static final int MAX_DECODE_DEPTH = 2;
    static final int MAX_ENCODED_TOKEN_LENGTH = 4096;
    private static final int MAX_INPUT_LENGTH = 16_384;
    private static final int PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String REDACTED = "***";
    private static final String CREDENTIAL_KEY = "(?:"
            + "aws[-_ ]?access[-_ ]?key(?:[-_ ]?id)?|aws[-_ ]?secret[-_ ]?access[-_ ]?key|"
            + "aws[-_ ]?session[-_ ]?token|x[-_ ]?amz[-_ ]?security[-_ ]?token|"
            + "x[-_ ]?goog[-_ ]?signature|"
            + "x[-_ ]?api[-_ ]?key|api[-_ ]?key|subscription[-_ ]?key|"
            + "access[-_ ]?key(?:[-_ ]?id)?|secret[-_ ]?access[-_ ]?key|"
            + "access[-_ ]?token|refresh[-_ ]?token|id[-_ ]?token|session[-_ ]?token|token|"
            + "client[-_ ]?secret|secret|password|passwd|pwd|passphrase|"
            + "credential(?:s|[-_ ]?material)?|"
            + "private[-_ ]?key|x[-_ ]?amz[-_ ]?credential|x[-_ ]?amz[-_ ]?signature|"
            + "signature|sig)";
    private static final String SENSITIVE_KEY = "(?:authorization|proxy[-_ ]?authorization|"
            + CREDENTIAL_KEY + ")";
    private static final String ASSIGNMENT_KEY = CREDENTIAL_KEY;

    private static final Pattern AUTHORIZATION_WITH_SCHEME = Pattern.compile(
            "(\\b(?:proxy[-_ ]?)?authorization\\b\\s*[:=]\\s*)"
                    + "((?:bearer|basic)\\s+)([^\\s,;\\\"'&}\\]]+)",
            PATTERN_FLAGS
    );
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(\\b(?:proxy[-_ ]?)?authorization\\b\\s*[:=]\\s*)"
                    + "([^\\s,;\\\"'&}\\]]+)",
            PATTERN_FLAGS
    );
    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "(\\b(?:bearer|basic)\\s+)([^\\s,;\\\"'&}\\]]+)",
            PATTERN_FLAGS
    );
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "([?&]" + SENSITIVE_KEY + "\\s*=\\s*)([^&#\\r\\n]*)",
            PATTERN_FLAGS
    );
    private static final Pattern DOUBLE_QUOTED_JSON_SECRET = Pattern.compile(
            "(\\\"" + SENSITIVE_KEY + "\\\"\\s*:\\s*)\\\"(?:\\\\.|[^\\\"\\\\])*\\\"",
            PATTERN_FLAGS
    );
    private static final Pattern SINGLE_QUOTED_JSON_SECRET = Pattern.compile(
            "('" + SENSITIVE_KEY + "'\\s*:\\s*)'(?:\\\\.|[^'\\\\])*'",
            PATTERN_FLAGS
    );
    private static final Pattern DOUBLE_QUOTED_ASSIGNMENT = Pattern.compile(
            "(\\b" + ASSIGNMENT_KEY + "\\b\\s*[:=]\\s*)\\\"(?:\\\\.|[^\\\"\\\\])*\\\"",
            PATTERN_FLAGS
    );
    private static final Pattern SINGLE_QUOTED_ASSIGNMENT = Pattern.compile(
            "(\\b" + ASSIGNMENT_KEY + "\\b\\s*[:=]\\s*)'(?:\\\\.|[^'\\\\])*'",
            PATTERN_FLAGS
    );
    private static final Pattern UNQUOTED_ASSIGNMENT = Pattern.compile(
            "(\\b" + ASSIGNMENT_KEY + "\\b\\s*[:=]\\s*)(?![\\\"'])([^\\s,;&}\\]]+)",
            PATTERN_FLAGS
    );
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN(?: [A-Z0-9]+)? PRIVATE KEY-----.*?-----END(?: [A-Z0-9]+)? PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern OPENAI_KEY = Pattern.compile(
            "(?<![A-Za-z0-9])sk-(?:proj-)?[A-Za-z0-9_-]{4,}",
            PATTERN_FLAGS
    );
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile(
            "(?<![A-Z0-9])(AKIA|ASIA)[A-Z0-9]{12,}(?![A-Z0-9])",
            PATTERN_FLAGS
    );
    private static final Pattern JWT = Pattern.compile(
            "(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"
                    + "(?![A-Za-z0-9_-])"
    );
    private static final Pattern BASE64_TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9_+/%-])([A-Za-z0-9_+/-]{8,}={0,2})"
                    + "(?![A-Za-z0-9_+/%-])(?!\\*{3})"
    );
    private static final Pattern WHOLE_BASE64_TOKEN = Pattern.compile("[A-Za-z0-9_+/-]{8,}={0,2}");
    private static final Pattern RESIDUAL_PERCENT_SEGMENT = Pattern.compile(
            "[^\\s,;&]*%[0-9a-fA-F]{2}[^\\s,;&]*"
    );
    private static final Pattern ESCAPED_DOUBLE_QUOTE = Pattern.compile("(?i)\\\\(?:u0022|x22|\\\")");
    private static final Pattern ESCAPED_SINGLE_QUOTE = Pattern.compile("(?i)\\\\(?:u0027|x27|')");
    private static final Pattern ESCAPED_COLON = Pattern.compile("(?i)\\\\(?:u003a|x3a)");
    private static final Pattern ESCAPED_EQUALS = Pattern.compile("(?i)\\\\(?:u003d|x3d)");

    private ProviderErrorSanitizer() {
    }

    /**
     * Detects high-confidence credential material without returning or logging the inspected value.
     * This shares the transport error grammar so persistence and messaging boundaries cannot drift
     * to a weaker definition of API keys, authorization values, private keys, or encoded credentials.
     */
    public static boolean containsSensitiveMaterial(String value) {
        return SensitiveMaterialDetector.containsSensitiveMaterial(value);
    }

    public static String safeReason(Throwable exception, String... credentialMaterials) {
        Objects.requireNonNull(exception, "供应商异常不能为空");
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        String sanitized = boundedInput(message);
        sanitized = redactKnownCredentialRepresentations(sanitized, credentialMaterials);
        sanitized = redactStructuredSecrets(sanitized);
        sanitized = redactSensitiveBase64(sanitized, credentialMaterials);
        for (int depth = 0; depth < MAX_DECODE_DEPTH; depth++) {
            sanitized = decodePercentPass(sanitized);
            sanitized = normalizeEscapedDelimiters(sanitized);
            sanitized = redactKnownCredentialRepresentations(sanitized, credentialMaterials);
            sanitized = redactStructuredSecrets(sanitized);
            sanitized = redactSensitiveBase64(sanitized, credentialMaterials);
        }
        sanitized = RESIDUAL_PERCENT_SEGMENT.matcher(sanitized).replaceAll(REDACTED);
        sanitized = redactStructuredSecrets(sanitized);
        sanitized = redactKnownCredentialRepresentations(sanitized, credentialMaterials);
        return truncate(sanitized);
    }

    private static String boundedInput(String value) {
        if (value.length() <= MAX_INPUT_LENGTH) {
            return value;
        }
        int end = MAX_INPUT_LENGTH;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String normalizeEscapedDelimiters(String value) {
        String normalized = ESCAPED_DOUBLE_QUOTE.matcher(value).replaceAll("\\\"");
        normalized = ESCAPED_SINGLE_QUOTE.matcher(normalized).replaceAll("'");
        normalized = ESCAPED_COLON.matcher(normalized).replaceAll(":");
        return ESCAPED_EQUALS.matcher(normalized).replaceAll("=");
    }

    private static String decodePercentPass(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            if (value.charAt(index) != '%') {
                decoded.append(value.charAt(index++));
                continue;
            }
            if (!isPercentEscape(value, index)) {
                decoded.append(REDACTED);
                index++;
                continue;
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (isPercentEscape(value, index)) {
                bytes.write(Integer.parseInt(value.substring(index + 1, index + 3), 16));
                index += 3;
            }
            String strictUtf8 = decodeStrictUtf8(bytes.toByteArray());
            decoded.append(strictUtf8 == null ? REDACTED : strictUtf8);
        }
        return decoded.toString();
    }

    private static boolean isPercentEscape(String value, int index) {
        return index + 2 < value.length()
                && value.charAt(index) == '%'
                && Character.digit(value.charAt(index + 1), 16) >= 0
                && Character.digit(value.charAt(index + 2), 16) >= 0;
    }

    private static String decodeStrictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static String redactStructuredSecrets(String value) {
        String redacted = replaceSecretValue(QUERY_SECRET, value, REDACTED);
        redacted = replaceAuthorization(redacted);
        redacted = replaceSecretValue(DOUBLE_QUOTED_JSON_SECRET, redacted, "\"***\"");
        redacted = replaceSecretValue(SINGLE_QUOTED_JSON_SECRET, redacted, "'***'");
        redacted = replaceSecretValue(DOUBLE_QUOTED_ASSIGNMENT, redacted, "\"***\"");
        redacted = replaceSecretValue(SINGLE_QUOTED_ASSIGNMENT, redacted, "'***'");
        redacted = replaceSecretValue(UNQUOTED_ASSIGNMENT, redacted, REDACTED);
        redacted = AUTH_SCHEME.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = PRIVATE_KEY.matcher(redacted).replaceAll(REDACTED);
        redacted = JWT.matcher(redacted).replaceAll(REDACTED);
        redacted = AWS_ACCESS_KEY.matcher(redacted).replaceAll("$1***");
        return OPENAI_KEY.matcher(redacted).replaceAll("sk-***");
    }

    private static String replaceAuthorization(String value) {
        Matcher matcher = AUTHORIZATION_WITH_SCHEME.matcher(value);
        StringBuilder redacted = new StringBuilder(value.length());
        while (matcher.find()) {
            matcher.appendReplacement(
                    redacted,
                    Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + REDACTED)
            );
        }
        matcher.appendTail(redacted);

        matcher = AUTHORIZATION.matcher(redacted.toString());
        StringBuilder genericRedacted = new StringBuilder(redacted.length());
        while (matcher.find()) {
            String valueToken = matcher.group(2);
            String replacement = valueToken.equalsIgnoreCase("bearer") || valueToken.equalsIgnoreCase("basic")
                    ? matcher.group()
                    : matcher.group(1) + REDACTED;
            matcher.appendReplacement(genericRedacted, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(genericRedacted);
        return genericRedacted.toString();
    }

    private static String replaceSecretValue(Pattern pattern, String value, String replacementValue) {
        Matcher matcher = pattern.matcher(value);
        StringBuilder redacted = new StringBuilder(value.length());
        while (matcher.find()) {
            matcher.appendReplacement(
                    redacted,
                    Matcher.quoteReplacement(matcher.group(1) + replacementValue)
            );
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }

    private static String redactKnownCredentialRepresentations(String value, String[] credentialMaterials) {
        String redacted = value;
        for (String representation : credentialRepresentations(credentialMaterials)) {
            redacted = Pattern.compile(Pattern.quote(representation), PATTERN_FLAGS)
                    .matcher(redacted)
                    .replaceAll(REDACTED);
        }
        return redacted;
    }

    private static List<String> credentialRepresentations(String[] credentialMaterials) {
        if (credentialMaterials == null || credentialMaterials.length == 0) {
            return List.of();
        }
        Set<String> representations = new LinkedHashSet<>();
        for (String credential : credentialMaterials) {
            if (credential == null || credential.isBlank()) {
                continue;
            }
            byte[] bytes = credential.getBytes(StandardCharsets.UTF_8);
            String formEncoded = URLEncoder.encode(credential, StandardCharsets.UTF_8);
            representations.add(credential);
            representations.add(formEncoded);
            representations.add(formEncoded.replace("+", "%20"));
            representations.add(percentEncodeEveryByte(bytes));
            representations.add(Base64.getEncoder().encodeToString(bytes));
            representations.add(Base64.getEncoder().withoutPadding().encodeToString(bytes));
            representations.add(Base64.getUrlEncoder().encodeToString(bytes));
            representations.add(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            representations.add(credential.replace("\\", "\\\\").replace("\"", "\\\""));
        }
        List<String> ordered = new ArrayList<>(representations);
        ordered.removeIf(String::isBlank);
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        return ordered;
    }

    private static String percentEncodeEveryByte(byte[] bytes) {
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte value : bytes) {
            encoded.append('%').append(String.format("%02X", value & 0xff));
        }
        return encoded.toString();
    }

    private static String redactSensitiveBase64(String value, String[] credentialMaterials) {
        Matcher matcher = BASE64_TOKEN.matcher(value);
        StringBuilder redacted = new StringBuilder(value.length());
        while (matcher.find()) {
            String token = matcher.group(1);
            boolean sensitive = isSensitiveBase64(token, credentialMaterials, 0);
            matcher.appendReplacement(
                    redacted,
                    Matcher.quoteReplacement(sensitive ? REDACTED : token)
            );
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }

    private static boolean isSensitiveBase64(String token, String[] credentialMaterials, int depth) {
        if (token.length() > MAX_ENCODED_TOKEN_LENGTH) {
            return true;
        }
        if (!looksLikeEncodedToken(token)) {
            return false;
        }

        for (Base64.Decoder decoder : new Base64.Decoder[]{Base64.getDecoder(), Base64.getUrlDecoder()}) {
            byte[] decodedBytes;
            try {
                decodedBytes = decoder.decode(padBase64(token));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String decoded = decodeStrictUtf8(decodedBytes);
            if (decoded == null) {
                return true;
            }
            String canonical = canonicalizeForInspection(decoded);
            if (containsKnownCredential(canonical, credentialMaterials) || containsSensitiveSyntax(canonical)) {
                return true;
            }
            String nested = canonical.trim();
            if (WHOLE_BASE64_TOKEN.matcher(nested).matches()) {
                if (depth + 1 >= MAX_DECODE_DEPTH) {
                    return true;
                }
                if (isSensitiveBase64(nested, credentialMaterials, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsSensitiveMaterialCandidate(String value) {
        String candidate = value;
        for (int depth = 0; depth <= MAX_DECODE_DEPTH; depth++) {
            if (containsSensitiveSyntax(candidate) || containsHighConfidenceSensitiveBase64(candidate)) {
                return true;
            }
            if (depth < MAX_DECODE_DEPTH) {
                candidate = normalizeEscapedDelimiters(decodePercentPass(candidate));
            }
        }
        return false;
    }

    private static boolean containsHighConfidenceSensitiveBase64(String value) {
        Matcher matcher = BASE64_TOKEN.matcher(value);
        while (matcher.find()) {
            if (isHighConfidenceSensitiveBase64(matcher.group(1), 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHighConfidenceSensitiveBase64(String token, int depth) {
        if (token.length() > MAX_ENCODED_TOKEN_LENGTH || !looksLikeEncodedToken(token)) {
            return false;
        }
        for (Base64.Decoder decoder : new Base64.Decoder[]{Base64.getDecoder(), Base64.getUrlDecoder()}) {
            byte[] decodedBytes;
            try {
                decodedBytes = decoder.decode(padBase64(token));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String decoded = decodeStrictUtf8(decodedBytes);
            if (decoded == null) {
                continue;
            }
            String canonical = canonicalizeForInspection(decoded);
            if (containsSensitiveSyntax(canonical)
                    || containsSensitiveSyntax(canonical.replace('+', ' '))) {
                return true;
            }
            String nested = canonical.trim();
            if (depth + 1 < MAX_DECODE_DEPTH
                    && WHOLE_BASE64_TOKEN.matcher(nested).matches()
                    && isHighConfidenceSensitiveBase64(nested, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeEncodedToken(String token) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean padding = false;
        boolean alphabetMarker = false;
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            upper |= Character.isUpperCase(value);
            lower |= Character.isLowerCase(value);
            digit |= Character.isDigit(value);
            padding |= value == '=';
            alphabetMarker |= value == '+' || value == '/';
        }
        return padding || (upper && lower && digit) || (alphabetMarker && (upper || digit));
    }

    private static String canonicalizeForInspection(String value) {
        String canonical = value;
        for (int depth = 0; depth < MAX_DECODE_DEPTH; depth++) {
            canonical = decodePercentPass(canonical);
            canonical = normalizeEscapedDelimiters(canonical);
        }
        return canonical;
    }

    private static boolean containsKnownCredential(String value, String[] credentialMaterials) {
        if (credentialMaterials == null) {
            return false;
        }
        for (String credential : credentialMaterials) {
            if (credential != null && !credential.isBlank()
                    && Pattern.compile(Pattern.quote(credential), PATTERN_FLAGS).matcher(value).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSensitiveSyntax(String value) {
        return AUTHORIZATION_WITH_SCHEME.matcher(value).find()
                || AUTHORIZATION.matcher(value).find()
                || AUTH_SCHEME.matcher(value).find()
                || QUERY_SECRET.matcher(value).find()
                || DOUBLE_QUOTED_JSON_SECRET.matcher(value).find()
                || SINGLE_QUOTED_JSON_SECRET.matcher(value).find()
                || DOUBLE_QUOTED_ASSIGNMENT.matcher(value).find()
                || SINGLE_QUOTED_ASSIGNMENT.matcher(value).find()
                || UNQUOTED_ASSIGNMENT.matcher(value).find()
                || PRIVATE_KEY.matcher(value).find()
                || OPENAI_KEY.matcher(value).find()
                || AWS_ACCESS_KEY.matcher(value).find()
                || JWT.matcher(value).find();
    }

    private static String padBase64(String token) {
        return token + "=".repeat((4 - token.length() % 4) % 4);
    }

    private static String truncate(String value) {
        if (value.length() <= MAX_DIAGNOSTIC_LENGTH) {
            return value;
        }
        int end = MAX_DIAGNOSTIC_LENGTH;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
