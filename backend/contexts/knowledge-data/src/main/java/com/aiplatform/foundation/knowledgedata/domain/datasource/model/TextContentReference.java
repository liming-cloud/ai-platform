package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 数据库保存的不可变文本对象引用和完整性事实。
 */
public record TextContentReference(
        int version,
        String objectKey,
        String sha256,
        long byteSize,
        String mediaType,
        Instant createdAt) implements ValueObject {

    public static final long MAX_BYTE_SIZE = 20L * 1024 * 1024;
    public static final String UTF8_TEXT = "text/plain; charset=UTF-8";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public TextContentReference {
        if (version < 1) {
            throw new IllegalArgumentException("内容版本必须大于零");
        }
        objectKey = requireObjectKey(objectKey);
        Objects.requireNonNull(sha256, "内容摘要不能为空");
        if (!SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("内容摘要格式不正确");
        }
        if (byteSize < 1 || byteSize > MAX_BYTE_SIZE) {
            throw new IllegalArgumentException("文本内容字节数超过限制");
        }
        if (!UTF8_TEXT.equals(mediaType)) {
            throw new IllegalArgumentException("只支持 UTF-8 纯文本");
        }
        Objects.requireNonNull(createdAt, "内容创建时间不能为空");
    }

    private static String requireObjectKey(String value) {
        Objects.requireNonNull(value, "对象引用不能为空");
        if (value.isBlank()
                || value.length() > 1024
                || value.startsWith("/")
                || value.endsWith("/")
                || value.contains("..")
                || !value.equals(value.trim())) {
            throw new IllegalArgumentException("对象引用格式不正确");
        }
        for (var index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException("对象引用不能包含控制字符");
            }
        }
        return value;
    }
}
