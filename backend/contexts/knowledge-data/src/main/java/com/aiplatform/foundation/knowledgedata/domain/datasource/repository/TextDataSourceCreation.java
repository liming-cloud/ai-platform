package com.aiplatform.foundation.knowledgedata.domain.datasource.repository;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextDataSource;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 原子创建文本数据源的持久化请求。
 */
public record TextDataSourceCreation(
        TextDataSource source,
        String idempotencyKey,
        String requestFingerprint) {

    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    public TextDataSourceCreation {
        Objects.requireNonNull(source, "文本数据源不能为空");
        idempotencyKey = requireIdempotencyKey(idempotencyKey);
        requestFingerprint = requireFingerprint(requestFingerprint);
    }

    static String requireIdempotencyKey(String value) {
        Objects.requireNonNull(value, "幂等键不能为空");
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 200) {
            throw new IllegalArgumentException("幂等键格式不正确");
        }
        return value;
    }

    static String requireFingerprint(String value) {
        Objects.requireNonNull(value, "请求指纹不能为空");
        if (!FINGERPRINT.matcher(value).matches()) {
            throw new IllegalArgumentException("请求指纹格式不正确");
        }
        return value;
    }
}
