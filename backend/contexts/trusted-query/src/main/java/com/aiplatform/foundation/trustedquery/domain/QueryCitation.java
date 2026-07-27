package com.aiplatform.foundation.trustedquery.domain;

import java.util.Objects;

/**
 * 回答所引用的已授权索引分块。
 */
public record QueryCitation(
        int sequence,
        long dataSourceId,
        int contentVersion,
        String chunkId,
        int ordinal,
        String quote,
        double score) {

    public QueryCitation {
        if (sequence < 1 || contentVersion < 1 || ordinal < 0) {
            throw new IllegalArgumentException("引用序号、内容版本和分块顺序不正确");
        }
        if (dataSourceId <= 0) {
            throw new IllegalArgumentException("引用数据源标识必须是正整数");
        }
        Objects.requireNonNull(chunkId, "引用分块标识不能为空");
        Objects.requireNonNull(quote, "引用摘录不能为空");
        if (chunkId.isBlank()
                || quote.isBlank()
                || quote.codePointCount(0, quote.length()) > 1000
                || !Double.isFinite(score)
                || score < -1.0
                || score > 1.0) {
            throw new IllegalArgumentException("引用内容或相似度不正确");
        }
    }
}
