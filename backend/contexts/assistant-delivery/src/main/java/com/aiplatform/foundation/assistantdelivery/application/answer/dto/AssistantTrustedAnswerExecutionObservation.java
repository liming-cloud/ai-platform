package com.aiplatform.foundation.assistantdelivery.application.answer.dto;

/**
 * 可信回答执行到失败点时可复核的最小过程事实。
 *
 * <p>候选数量为空表示检索尚未完成，不能解释为零个候选。</p>
 */
public record AssistantTrustedAnswerExecutionObservation(
        Integer candidateCount,
        long chatModelRevision,
        long embeddingModelRevision) {

    public AssistantTrustedAnswerExecutionObservation {
        if (candidateCount != null && candidateCount < 0
                || chatModelRevision < 1 || embeddingModelRevision < 1) {
            throw new IllegalArgumentException("可信回答执行事实不正确");
        }
    }
}
