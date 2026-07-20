package com.aiplatform.knowledge.application.retrieval.result;

import com.aiplatform.knowledge.domain.retrieval.RetrievalEvidenceIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识检索结果，显式区分证据召回状态与可选答案生成状态，避免模型在空上下文下编造。
 */
public record RetrieveKnowledgeResult(
        boolean answerable,
        List<RetrievedKnowledgeChunk> chunks,
        String noAnswerReason,
        String generatedAnswer,
        String answerModelCode,
        String answerGenerationReason,
        RetrievalAuthorizationSnapshot authorizationSnapshot,
        int filteredCount,
        int filteredCitationCount
) {
    public RetrieveKnowledgeResult(
            boolean answerable,
            List<RetrievedKnowledgeChunk> chunks,
            String noAnswerReason,
            String generatedAnswer,
            String answerModelCode,
            String answerGenerationReason
    ) {
        this(answerable, chunks, noAnswerReason, generatedAnswer, answerModelCode, answerGenerationReason,
                RetrievalAuthorizationSnapshot.empty(), 0, 0);
    }

    public RetrieveKnowledgeResult {
        chunks = uniqueChunks(copyRequiredList(chunks, "检索切片"));
        if (authorizationSnapshot == null) {
            throw new IllegalArgumentException("授权快照不能为空");
        }
        if (filteredCount < 0 || filteredCitationCount < 0 || filteredCitationCount > filteredCount) {
            throw new IllegalArgumentException("过滤计数不合法");
        }
        if (answerable) {
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("可回答结果必须包含检索切片");
            }
            if (noAnswerReason != null && !noAnswerReason.isBlank()) {
                throw new IllegalArgumentException("可回答结果不能携带无答案原因");
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
                throw new IllegalArgumentException("无答案结果不能携带检索切片");
            }
            noAnswerReason = requireText(noAnswerReason, "无答案原因");
            generatedAnswer = null;
            answerModelCode = null;
            answerGenerationReason = null;
        }
    }

    public static RetrieveKnowledgeResult noAnswer() {
        return new RetrieveKnowledgeResult(false, List.of(), "未召回到具备权限且可信的知识片段", null, null, null);
    }

    public static RetrieveKnowledgeResult answerable(List<RetrievedKnowledgeChunk> chunks) {
        return new RetrieveKnowledgeResult(true, chunks, null, null, null, null);
    }

    public RetrieveKnowledgeResult withGeneratedAnswer(String answerText, String modelCode) {
        if (!answerable) {
            throw new IllegalStateException("无答案结果不能生成回答");
        }
        return new RetrieveKnowledgeResult(true, chunks, null, answerText, modelCode, null,
                authorizationSnapshot, filteredCount, filteredCitationCount);
    }

    public RetrieveKnowledgeResult withAnswerGenerationReason(String reason) {
        if (!answerable) {
            throw new IllegalStateException("无答案结果不能设置答案生成原因");
        }
        return new RetrieveKnowledgeResult(true, chunks, null, null, null, reason,
                authorizationSnapshot, filteredCount, filteredCitationCount);
    }

    public RetrieveKnowledgeResult withSecurityEvidence(
            RetrievalAuthorizationSnapshot snapshot,
            int totalFilteredCount,
            int citationFilteredCount
    ) {
        return new RetrieveKnowledgeResult(
                answerable,
                chunks,
                noAnswerReason,
                generatedAnswer,
                answerModelCode,
                answerGenerationReason,
                snapshot,
                totalFilteredCount,
                citationFilteredCount
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

    private static List<RetrievedKnowledgeChunk> uniqueChunks(List<RetrievedKnowledgeChunk> chunks) {
        Map<RetrievalEvidenceIdentity, RetrievedKnowledgeChunk> unique = new LinkedHashMap<>();
        chunks.forEach(chunk -> unique.putIfAbsent(chunk.evidenceIdentity(), chunk));
        return List.copyOf(unique.values());
    }
}
