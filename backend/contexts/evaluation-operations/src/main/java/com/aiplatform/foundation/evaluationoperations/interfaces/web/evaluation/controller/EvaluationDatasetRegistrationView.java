package com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import java.time.Instant;

/**
 * 评测数据集登记后的公开清单投影；不包含私有正文引用或正文。
 */
public record EvaluationDatasetRegistrationView(
        String id,
        String contentDigest,
        int entryCount,
        String classification,
        String retentionPolicyId,
        String status,
        long version,
        Instant createdAt) {

    static EvaluationDatasetRegistrationView from(EvaluationDataset dataset) {
        return new EvaluationDatasetRegistrationView(
                Long.toString(dataset.id().value()),
                dataset.contentDigest(),
                dataset.entryCount(),
                dataset.classification().name(),
                dataset.retentionPolicyId(),
                dataset.status().name(),
                dataset.version(),
                dataset.createdAt());
    }
}
