package com.aiplatform.foundation.knowledgedata.application.datasource.port.out.dataset;

import java.util.Locale;

/** 私有 CSV 对象的公开扩展引用。 */
public record CsvObjectReference(String objectKey, String contentDigest, long byteSize) {
    public static final String UTF8_CSV = "text/csv; charset=utf-8";
    public CsvObjectReference {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")
                || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new IllegalArgumentException("CSV 对象键格式不正确");
        }
        if (contentDigest == null || !contentDigest.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("CSV 内容摘要格式不正确");
        }
        contentDigest = contentDigest.toLowerCase(Locale.ROOT);
        if (byteSize < 1) {
            throw new IllegalArgumentException("CSV 字节数必须大于零");
        }
    }
}
