package com.aiplatform.foundation.knowledgedata.api.extension;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 向量存储扩展可消费的已验证索引分块。
 */
public final class IndexVector {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final UUID dataSourceId;
    private final int contentVersion;
    private final String chunkId;
    private final int ordinal;
    private final String text;
    private final String textDigest;
    private final float[] embedding;

    public IndexVector(
            UUID dataSourceId,
            int contentVersion,
            String chunkId,
            int ordinal,
            String text,
            String textDigest,
            float[] embedding) {
        this.dataSourceId = Objects.requireNonNull(
                dataSourceId,
                "数据源标识不能为空");
        if (contentVersion < 1) {
            throw new IllegalArgumentException("内容版本必须大于零");
        }
        this.contentVersion = contentVersion;
        this.chunkId = requireDigest(chunkId, "分块标识");
        if (ordinal < 0) {
            throw new IllegalArgumentException("分块顺序不能为负数");
        }
        this.ordinal = ordinal;
        this.text = Objects.requireNonNull(text, "分块文本不能为空");
        if (text.isBlank()) {
            throw new IllegalArgumentException("分块文本不能为空白");
        }
        this.textDigest = requireDigest(textDigest, "分块摘要");
        Objects.requireNonNull(embedding, "嵌入向量不能为空");
        if (embedding.length < 1 || embedding.length > 2000) {
            throw new IllegalArgumentException("嵌入向量维度超出平台范围");
        }
        for (var value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("嵌入向量只能包含有限数值");
            }
        }
        this.embedding = embedding.clone();
    }

    public UUID dataSourceId() {
        return dataSourceId;
    }

    public int contentVersion() {
        return contentVersion;
    }

    public String chunkId() {
        return chunkId;
    }

    public int ordinal() {
        return ordinal;
    }

    public String text() {
        return text;
    }

    public String textDigest() {
        return textDigest;
    }

    public int dimension() {
        return embedding.length;
    }

    public float[] embedding() {
        return embedding.clone();
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof IndexVector other
                && contentVersion == other.contentVersion
                && ordinal == other.ordinal
                && dataSourceId.equals(other.dataSourceId)
                && chunkId.equals(other.chunkId)
                && text.equals(other.text)
                && textDigest.equals(other.textDigest)
                && Arrays.equals(embedding, other.embedding);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(
                dataSourceId,
                contentVersion,
                chunkId,
                ordinal,
                text,
                textDigest)
                + Arrays.hashCode(embedding);
    }

    private static String requireDigest(String value, String field) {
        Objects.requireNonNull(value, field + "不能为空");
        if (!SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + "格式不正确");
        }
        return value;
    }
}
