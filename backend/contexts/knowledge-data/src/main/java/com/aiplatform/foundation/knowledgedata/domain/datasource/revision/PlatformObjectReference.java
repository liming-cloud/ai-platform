package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 平台托管范围内一个冻结对象的不可变元数据。
 *
 * <p>对象标识是平台内部不透明引用，不能替换为外部对象键或连接信息。</p>
 */
public record PlatformObjectReference(
        String objectId,
        String contentDigest,
        long byteSize,
        String mediaType,
        Instant freshAt) implements ValueObject {

    private static final Pattern ID_FORMAT = Pattern.compile("obj_[A-Za-z0-9]{16,128}");

    public PlatformObjectReference {
        objectId = requireObjectId(objectId);
        contentDigest = requireDigest(contentDigest);
        if (byteSize < 1) {
            throw new IllegalArgumentException("对象字节数必须大于零");
        }
        mediaType = requireMediaType(mediaType);
        freshAt = Objects.requireNonNull(freshAt, "对象新鲜度时间不能为空");
    }

    private static String requireObjectId(String value) {
        Objects.requireNonNull(value, "平台对象标识不能为空");
        if (!ID_FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("平台对象标识格式不正确");
        }
        return value;
    }

    private static String requireDigest(String value) {
        if (value == null || value.length() != 64
                || value.chars().anyMatch(character -> Character.digit(character, 16) < 0)) {
            throw new IllegalArgumentException("对象内容摘要必须是 64 位十六进制 SHA-256");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String requireMediaType(String value) {
        Objects.requireNonNull(value, "对象媒体类型不能为空");
        if (value.isBlank() || value.length() > 128 || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("对象媒体类型格式不正确");
        }
        return value;
    }
}
