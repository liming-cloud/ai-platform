package com.aiplatform.foundation.provider.openaicompatible;

import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 按火山引擎官方 HMAC-SHA256 规范为控制面请求生成签名头。 */
final class VolcengineSignerV4 {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private VolcengineSignerV4() {
    }

    static SignedHeaders sign(
            URI uri,
            String method,
            byte[] body,
            char[] accessKeyId,
            char[] secretAccessKey,
            String region,
            String service,
            Instant now) {
        var dateTime = DATE_TIME.format(now);
        var bodyHash = sha256Hex(body);
        var headers = new TreeMap<String, String>();
        headers.put("content-type", "application/json");
        headers.put("host", canonicalHost(uri));
        headers.put("x-content-sha256", bodyHash);
        headers.put("x-date", dateTime);
        var signedHeaders = String.join(";", headers.keySet());
        var canonicalHeaders = new StringBuilder();
        headers.forEach((name, value) -> canonicalHeaders.append(name).append(':').append(value).append('\n'));
        var canonicalRequest = String.join("\n",
                method,
                uri.getRawPath().isEmpty() ? "/" : uri.getRawPath(),
                canonicalQuery(uri),
                canonicalHeaders.toString(),
                signedHeaders,
                bodyHash);
        var date = dateTime.substring(0, 8);
        var scope = String.join("/", date, region, service, "request");
        var stringToSign = String.join("\n", "HMAC-SHA256", dateTime, scope,
                sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        var secretBytes = utf8(secretAccessKey);
        byte[] signingKey = null;
        try {
            signingKey = hmac(hmac(hmac(hmac(secretBytes, date), region), service), "request");
            var signature = HexFormat.of().formatHex(hmac(signingKey, stringToSign));
            return new SignedHeaders(
                    dateTime,
                    bodyHash,
                    "HMAC-SHA256 Credential=" + new String(accessKeyId) + '/' + scope
                            + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        } finally {
            Arrays.fill(secretBytes, (byte) 0);
            if (signingKey != null) {
                Arrays.fill(signingKey, (byte) 0);
            }
        }
    }

    private static String canonicalHost(URI uri) {
        var port = uri.getPort();
        if (port == -1 || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443)) {
            return uri.getHost();
        }
        return uri.getHost() + ':' + port;
    }

    private static String canonicalQuery(URI uri) {
        if (uri.getRawQuery() == null || uri.getRawQuery().isEmpty()) {
            return "";
        }
        return Arrays.stream(uri.getRawQuery().split("&"))
                .sorted()
                .reduce((left, right) -> left + '&' + right)
                .orElse("");
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("当前 JDK 不支持 HmacSHA256", exception);
        }
    }

    private static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static byte[] utf8(char[] value) {
        var encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        var bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) {
            Arrays.fill(encoded.array(), (byte) 0);
        }
        return bytes;
    }

    record SignedHeaders(String date, String contentSha256, String authorization) {
    }
}
