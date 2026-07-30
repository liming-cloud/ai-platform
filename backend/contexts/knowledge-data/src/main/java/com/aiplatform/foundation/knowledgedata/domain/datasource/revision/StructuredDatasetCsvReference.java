package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Locale;
import java.util.Objects;

/**
 * 结构化数据集修订冻结的平台私有 UTF-8 CSV 对象事实。
 *
 * <p>对象键仅由平台上传适配器生成，不包含桶名、端点或凭据，不能由知识管理员经公共接口指定。</p>
 */
public record StructuredDatasetCsvReference(
        String objectKey,
        String contentDigest,
        long byteSize) implements ValueObject {

    public static final String UTF8_CSV = "text/csv; charset=utf-8";

    public StructuredDatasetCsvReference {
        objectKey = requireObjectKey(objectKey);
        contentDigest = requireDigest(contentDigest);
        if (byteSize < 1) {
            throw new IllegalArgumentException("CSV 字节数必须大于零");
        }
    }

    private static String requireObjectKey(String value) {
        Objects.requireNonNull(value, "CSV 对象键不能为空");
        if (value.isBlank() || value.startsWith("/") || value.contains("..") || value.contains("\\")) {
            throw new IllegalArgumentException("CSV 对象键格式不正确");
        }
        return value;
    }

    private static String requireDigest(String value) {
        if (value == null || value.length() != 64
                || value.chars().anyMatch(character -> Character.digit(character, 16) < 0)) {
            throw new IllegalArgumentException("CSV 内容摘要必须是 64 位十六进制 SHA-256");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
