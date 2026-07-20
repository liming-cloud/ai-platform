package com.aiplatform.knowledge.presentation.retrieval.response;

import com.aiplatform.knowledge.application.retrieval.result.RetrieveKnowledgeResult;
import com.aiplatform.knowledge.application.retrieval.result.RetrievalAuthorizationSnapshot;
import java.util.List;
import java.util.Objects;

/**
 * 知识检索 HTTP 响应，明确返回可回答状态、引用切片、生成答案和无答案原因。
 */
public record RetrieveKnowledgeResponse(
        boolean answerable,
        List<RetrievedKnowledgeChunkResponse> chunks,
        String noAnswerReason,
        String generatedAnswer,
        String answerModelCode,
        String answerGenerationReason,
        RetrievalAuthorizationSnapshotResponse authorizationSnapshot,
        int filteredCount,
        int filteredCitationCount
) {
    public RetrieveKnowledgeResponse(
            boolean answerable,
            List<RetrievedKnowledgeChunkResponse> chunks,
            String noAnswerReason,
            String generatedAnswer,
            String answerModelCode,
            String answerGenerationReason
    ) {
        this(
                answerable,
                chunks,
                noAnswerReason,
                generatedAnswer,
                answerModelCode,
                answerGenerationReason,
                RetrievalAuthorizationSnapshotResponse.from(RetrievalAuthorizationSnapshot.empty()),
                0,
                0
        );
    }

    public RetrieveKnowledgeResponse {
        chunks = copyRequiredList(chunks, "检索切片响应");
        Objects.requireNonNull(authorizationSnapshot, "授权快照不能为空");
        if (filteredCount < 0 || filteredCitationCount < 0 || filteredCitationCount > filteredCount) {
            throw new IllegalArgumentException("过滤计数不合法");
        }
        // 兼容保留响应字段，但不向调用方暴露 ACL 过滤规模，内部值仅进入审计事件。
        filteredCount = 0;
        filteredCitationCount = 0;
        if (answerable) {
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("可回答响应必须包含检索切片");
            }
            if (noAnswerReason != null && !noAnswerReason.isBlank()) {
                throw new IllegalArgumentException("可回答响应不能携带无答案原因");
            }
            noAnswerReason = null;
            generatedAnswer = normalizeOptionalText(generatedAnswer);
            answerModelCode = normalizeOptionalText(answerModelCode);
            answerGenerationReason = normalizeOptionalText(answerGenerationReason);
            if (generatedAnswer == null && answerModelCode != null) {
                throw new IllegalArgumentException("未生成答案时不能携带回答模型编码");
            }
            if (generatedAnswer != null && answerModelCode == null) {
                throw new IllegalArgumentException("生成答案时必须携带回答模型编码");
            }
            if (generatedAnswer != null && answerGenerationReason != null) {
                throw new IllegalArgumentException("生成答案时不能携带答案生成原因");
            }
        } else {
            if (!chunks.isEmpty()) {
                throw new IllegalArgumentException("无答案响应不能携带检索切片");
            }
            noAnswerReason = requireText(noAnswerReason, "无答案原因");
            generatedAnswer = null;
            answerModelCode = null;
            answerGenerationReason = null;
        }
    }

    public static RetrieveKnowledgeResponse from(RetrieveKnowledgeResult result) {
        Objects.requireNonNull(result, "检索结果不能为空");
        return new RetrieveKnowledgeResponse(
                result.answerable(),
                result.chunks().stream().map(RetrievedKnowledgeChunkResponse::from).toList(),
                result.noAnswerReason(),
                result.generatedAnswer(),
                result.answerModelCode(),
                result.answerGenerationReason(),
                RetrievalAuthorizationSnapshotResponse.from(result.authorizationSnapshot()),
                0,
                0
        );
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private static <T> List<T> copyRequiredList(List<T> values, String fieldName) {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return values.stream()
                .map(value -> requireElement(value, fieldName))
                .toList();
    }

    private static <T> T requireElement(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
