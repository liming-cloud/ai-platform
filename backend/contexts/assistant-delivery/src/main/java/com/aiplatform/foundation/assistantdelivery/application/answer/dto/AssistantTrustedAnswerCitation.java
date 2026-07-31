package com.aiplatform.foundation.assistantdelivery.application.answer.dto;

import java.util.Objects;

/**
 * 本次可信回答已核验引用的最小瞬时元数据。
 */
public record AssistantTrustedAnswerCitation(
        int sequence,
        long dataSourceId,
        int contentVersion,
        String chunkId,
        int ordinal,
        String quote,
        double score) {

    public AssistantTrustedAnswerCitation {
        if (sequence < 1 || dataSourceId <= 0 || contentVersion < 1 || ordinal < 0) {
            throw new IllegalArgumentException("可信回答引用元数据不正确");
        }
        chunkId = requireText(chunkId, "引用分块标识不能为空");
        quote = requireText(quote, "引用摘录不能为空");
        if (!Double.isFinite(score) || score < -1.0 || score > 1.0) {
            throw new IllegalArgumentException("可信回答引用相似度不正确");
        }
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
