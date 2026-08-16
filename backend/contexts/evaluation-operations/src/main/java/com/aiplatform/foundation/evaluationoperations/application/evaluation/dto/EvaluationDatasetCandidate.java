package com.aiplatform.foundation.evaluationoperations.application.evaluation.dto;

import java.time.Instant;

/** 评测发起页可见的数据集安全摘要，不包含正文引用、摘要和内部修订。 */
public record EvaluationDatasetCandidate(
        String datasetReference,
        int entryCount,
        String classification,
        Instant createdAt) {
}
