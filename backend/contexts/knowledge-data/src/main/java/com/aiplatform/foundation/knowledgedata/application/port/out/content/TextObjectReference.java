package com.aiplatform.foundation.knowledgedata.application.port.out.content;

import java.time.Instant;
import java.util.Objects;

/** 对象存储扩展使用的冻结文本对象事实，不包含桶名或凭据。 */
public record TextObjectReference(int version, String objectKey, String sha256,
        long byteSize, String mediaType, Instant createdAt) {
    public static final String UTF8_TEXT = "text/plain; charset=UTF-8";
    public TextObjectReference {
        if (version < 1 || byteSize < 1 || byteSize > 20L * 1024 * 1024) {
            throw new IllegalArgumentException("文本对象版本或长度不正确");
        }
        if (objectKey == null || objectKey.isBlank() || objectKey.length() > 1024
                || objectKey.startsWith("/") || objectKey.endsWith("/")
                || objectKey.contains("..") || !objectKey.equals(objectKey.trim())
                || objectKey.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("文本对象引用格式不正确");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("文本内容摘要格式不正确");
        }
        if (!UTF8_TEXT.equals(mediaType)) {
            throw new IllegalArgumentException("只支持 UTF-8 纯文本");
        }
        Objects.requireNonNull(createdAt, "内容创建时间不能为空");
    }
}
