package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * 文件来源修订冻结的平台受控对象事实。
 *
 * <p>对象键只由平台存储适配器生成。该值对象不保存桶名或访问凭据，并以对象键、内容摘要、长度和媒体类型
 * 共同计算不可变定义摘要，避免同一内容被替换到不同对象后仍被误判为同一来源定义。</p>
 */
public record FileSourceObjectReference(
        String objectKey,
        String contentDigest,
        long byteSize,
        String mediaType) implements ValueObject {

    public static final String UTF8_TEXT = "text/plain; charset=utf-8";

    public FileSourceObjectReference {
        objectKey = requireObjectKey(objectKey);
        contentDigest = requireDigest(contentDigest);
        if (byteSize < 1) {
            throw new IllegalArgumentException("文件字节数必须大于零");
        }
        if (!UTF8_TEXT.equals(mediaType)) {
            throw new IllegalArgumentException("首个文件来源只支持 UTF-8 纯文本");
        }
    }

    /**
     * 返回同时覆盖对象位置与内容事实的不可变来源定义摘要。
     */
    public String definitionDigest() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, objectKey);
            update(digest, contentDigest);
            update(digest, Long.toString(byteSize));
            update(digest, mediaType);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String requireObjectKey(String value) {
        Objects.requireNonNull(value, "文件对象键不能为空");
        if (value.isBlank() || value.startsWith("/") || value.contains("..") || value.contains("\\")) {
            throw new IllegalArgumentException("文件对象键格式不正确");
        }
        return value;
    }

    private static String requireDigest(String value) {
        if (value == null || value.length() != 64
                || value.chars().anyMatch(character -> Character.digit(character, 16) < 0)) {
            throw new IllegalArgumentException("文件内容摘要必须是 64 位十六进制 SHA-256");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
