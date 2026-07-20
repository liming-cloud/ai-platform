package com.aiplatform.shared.kernel;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared high-confidence detector used by both provider diagnostics and approval evidence boundaries. */
public final class SensitiveMaterialDetector {
    private static final int MAX_DECODE_DEPTH = 2;
    private static final int MAX_ENCODED_TOKEN_LENGTH = 4_096;
    private static final int MAX_INPUT_LENGTH = 16_384;
    private static final int PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String CREDENTIAL_KEY = "(?:"
            + "aws[-_ ]?access[-_ ]?key(?:[-_ ]?id)?|aws[-_ ]?secret[-_ ]?access[-_ ]?key|"
            + "aws[-_ ]?session[-_ ]?token|x[-_ ]?amz[-_ ]?security[-_ ]?token|"
            + "x[-_ ]?goog[-_ ]?signature|x[-_ ]?api[-_ ]?key|api[-_ ]?key|subscription[-_ ]?key|"
            + "access[-_ ]?key(?:[-_ ]?id)?|secret[-_ ]?access[-_ ]?key|access[-_ ]?token|"
            + "refresh[-_ ]?token|id[-_ ]?token|session[-_ ]?token|token|client[-_ ]?secret|"
            + "secret|password|passwd|pwd|passphrase|credential(?:s|[-_ ]?material)?|"
            + "private[-_ ]?key|x[-_ ]?amz[-_ ]?credential|x[-_ ]?amz[-_ ]?signature|signature|sig)";
    private static final String SENSITIVE_KEY = "(?:authorization|proxy[-_ ]?authorization|"
            + CREDENTIAL_KEY + ")";
    private static final Pattern AUTHORIZATION_WITH_SCHEME = Pattern.compile(
            "\\b(?:proxy[-_ ]?)?authorization\\b\\s*[:=]\\s*(?:bearer|basic)\\s+"
                    + "[^\\s,;\\\"'&}\\]]+",
            PATTERN_FLAGS
    );
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "\\b(?:proxy[-_ ]?)?authorization\\b\\s*[:=]\\s*[^\\s,;\\\"'&}\\]]+",
            PATTERN_FLAGS
    );
    private static final Pattern AUTH_SCHEME = Pattern.compile(
            "\\b(?:bearer|basic)\\s+[^\\s,;\\\"'&}\\]]+",
            PATTERN_FLAGS
    );
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "[?&]" + SENSITIVE_KEY + "\\s*=\\s*[^&#\\r\\n]*",
            PATTERN_FLAGS
    );
    private static final Pattern QUOTED_SECRET = Pattern.compile(
            "[\\\"']" + SENSITIVE_KEY + "[\\\"']\\s*:\\s*[\\\"'](?:\\\\.|[^\\\"'\\\\])*[\\\"']",
            PATTERN_FLAGS
    );
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "\\b" + CREDENTIAL_KEY + "\\b\\s*[:=]\\s*(?:[\\\"'](?:\\\\.|[^\\\"'\\\\])*[\\\"']|"
                    + "[^\\s,;&}\\]]+)",
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
            "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"
                    + "(?![A-Za-z0-9_-])"
    );
    private static final Pattern BASE64_TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9_+/%-])([A-Za-z0-9_+/-]{8,}={0,2})(?![A-Za-z0-9_+/%-])"
    );
    private static final Pattern WHOLE_BASE64_TOKEN = Pattern.compile("[A-Za-z0-9_+/-]{8,}={0,2}");
    private static final Pattern ESCAPED_DOUBLE_QUOTE = Pattern.compile("(?i)\\\\(?:u0022|x22|\\\")");
    private static final Pattern ESCAPED_SINGLE_QUOTE = Pattern.compile("(?i)\\\\(?:u0027|x27|')");
    private static final Pattern ESCAPED_COLON = Pattern.compile("(?i)\\\\(?:u003a|x3a)");
    private static final Pattern ESCAPED_EQUALS = Pattern.compile("(?i)\\\\(?:u003d|x3d)");
    private static final Pattern JSON_STRING_LEAF = Pattern.compile(
            "\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\""
    );
    private static final Set<String> JSON_CREDENTIAL_KEYS = Set.of(
            "accesskey", "accesskeyid", "secretaccesskey", "sessiontoken",
            "awsaccesskeyid", "awssecretaccesskey", "awssessiontoken",
            "privatekey", "privatekeyid", "clientid", "clientsecret",
            "refreshtoken", "accesstoken", "idtoken", "token"
    );

    private SensitiveMaterialDetector() {
    }

    public static boolean containsSensitiveMaterial(String value, String... knownCredentials) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.length() > MAX_INPUT_LENGTH) {
            return true;
        }
        for (String representation : credentialRepresentations(knownCredentials)) {
            if (containsIgnoreCase(value, representation)) {
                return true;
            }
        }
        return containsCandidate(value) || containsCandidate(value.replace('+', ' '));
    }

    private static boolean containsCandidate(String value) {
        String candidate = value;
        for (int depth = 0; depth <= MAX_DECODE_DEPTH; depth++) {
            if (containsSensitiveSyntax(candidate) || containsSensitiveBase64(candidate)) {
                return true;
            }
            if (depth < MAX_DECODE_DEPTH) {
                candidate = normalizeEscapedDelimiters(decodePercentPass(candidate));
            }
        }
        return false;
    }

    private static boolean containsSensitiveSyntax(String value) {
        return AUTHORIZATION_WITH_SCHEME.matcher(value).find()
                || AUTHORIZATION.matcher(value).find()
                || AUTH_SCHEME.matcher(value).find()
                || QUERY_SECRET.matcher(value).find()
                || QUOTED_SECRET.matcher(value).find()
                || ASSIGNMENT.matcher(value).find()
                || PRIVATE_KEY.matcher(value).find()
                || OPENAI_KEY.matcher(value).find()
                || AWS_ACCESS_KEY.matcher(value).find()
                || JWT.matcher(value).find();
    }

    private static boolean containsSensitiveBase64(String value) {
        Matcher matcher = BASE64_TOKEN.matcher(value);
        while (matcher.find()) {
            if (isSensitiveBase64(matcher.group(1), 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSensitiveBase64(String token, int depth) {
        if (token.length() > MAX_ENCODED_TOKEN_LENGTH || !looksLikeEncodedToken(token)) {
            return false;
        }
        for (Base64.Decoder decoder : new Base64.Decoder[]{Base64.getDecoder(), Base64.getUrlDecoder()}) {
            final byte[] decodedBytes;
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
            if (containsSensitiveSyntax(canonical) || containsSensitiveSyntax(canonical.replace('+', ' '))) {
                return true;
            }
            String nested = canonical.trim();
            if (depth + 1 < MAX_DECODE_DEPTH
                    && WHOLE_BASE64_TOKEN.matcher(nested).matches()
                    && isSensitiveBase64(nested, depth + 1)) {
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
            canonical = normalizeEscapedDelimiters(decodePercentPass(canonical));
        }
        return canonical;
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
            if (!isPercentEscape(value, index)) {
                decoded.append(value.charAt(index++));
                continue;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (isPercentEscape(value, index)) {
                bytes.write(Integer.parseInt(value.substring(index + 1, index + 3), 16));
                index += 3;
            }
            String strictUtf8 = decodeStrictUtf8(bytes.toByteArray());
            decoded.append(strictUtf8 == null ? "***" : strictUtf8);
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

    private static List<String> credentialRepresentations(String[] credentials) {
        if (credentials == null || credentials.length == 0) {
            return List.of();
        }
        Set<String> representations = new LinkedHashSet<>();
        for (String credential : credentials) {
            if (credential == null || credential.isBlank()) {
                continue;
            }
            addCredentialRepresentations(representations, credential);
            addCredentialRepresentations(representations, credential.trim());
            Matcher leaves = JSON_STRING_LEAF.matcher(credential);
            while (leaves.find()) {
                String key = normalizeJsonCredentialKey(unescapeJsonString(leaves.group(1)));
                if (!JSON_CREDENTIAL_KEYS.contains(key)) {
                    continue;
                }
                String leaf = unescapeJsonString(leaves.group(2));
                addCredentialRepresentations(representations, leaf);
                addCredentialRepresentations(representations, leaf.trim());
            }
        }
        List<String> ordered = new ArrayList<>(representations);
        ordered.removeIf(String::isBlank);
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        return ordered;
    }

    private static void addCredentialRepresentations(Set<String> representations, String credential) {
        if (credential.isBlank()) {
            return;
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

    private static String unescapeJsonString(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                result.append(current);
                continue;
            }
            char escaped = value.charAt(++index);
            switch (escaped) {
                case '\"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) {
                        result.append("\\u");
                        break;
                    }
                    String hex = value.substring(index + 1, index + 5);
                    try {
                        result.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    } catch (NumberFormatException exception) {
                        result.append("\\u").append(hex);
                        index += 4;
                    }
                }
                default -> result.append(escaped);
            }
        }
        return result.toString();
    }

    private static String normalizeJsonCredentialKey(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[-_\\s]", "");
    }

    private static String percentEncodeEveryByte(byte[] bytes) {
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte value : bytes) {
            encoded.append('%').append(String.format("%02X", value & 0xff));
        }
        return encoded.toString();
    }

    private static boolean containsIgnoreCase(String source, String value) {
        return Pattern.compile(Pattern.quote(value), PATTERN_FLAGS).matcher(source).find();
    }

    private static String padBase64(String token) {
        return token + "=".repeat((4 - token.length() % 4) % 4);
    }
}
