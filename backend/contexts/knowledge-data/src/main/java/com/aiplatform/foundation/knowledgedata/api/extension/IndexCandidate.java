package com.aiplatform.foundation.knowledgedata.api.extension;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 严格授权范围内的单个检索候选。
 */
public record IndexCandidate(
        long dataSourceId,
        int contentVersion,
        String chunkId,
        int ordinal,
        String content,
        double score) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public IndexCandidate {
        if (dataSourceId <= 0) {
            throw new IllegalArgumentException("数据源标识必须是正整数");
        }
        if (contentVersion < 1) {
            throw new IllegalArgumentException("内容版本必须大于零");
        }
        Objects.requireNonNull(chunkId, "分块标识不能为空");
        if (!SHA256.matcher(chunkId).matches()) {
            throw new IllegalArgumentException("分块标识格式不正确");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("分块顺序不能为负数");
        }
        Objects.requireNonNull(content, "候选内容不能为空");
        if (content.isBlank()) {
            throw new IllegalArgumentException("候选内容不能为空白");
        }
        if (!Double.isFinite(score) || score < -1 || score > 1) {
            throw new IllegalArgumentException("候选相似度超出余弦范围");
        }
    }
}
