package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 可被运行冻结的无正文数据集版本事实。
 */
public record EvaluationDatasetSnapshot(
        EvaluationDatasetId datasetId,
        long datasetVersion,
        String contentDigest) implements ValueObject {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public EvaluationDatasetSnapshot {
        datasetId = Objects.requireNonNull(datasetId, "评测数据集标识不能为空");
        if (datasetVersion < 0L || contentDigest == null || !SHA256.matcher(contentDigest).matches()) {
            throw new IllegalArgumentException("评测数据集快照不完整");
        }
    }

    /**
     * 从已存在的数据集根投影不含正文的冻结引用。
     */
    public static EvaluationDatasetSnapshot from(EvaluationDataset dataset) {
        dataset = Objects.requireNonNull(dataset, "评测数据集不能为空");
        return new EvaluationDatasetSnapshot(dataset.id(), dataset.version(), dataset.contentDigest());
    }
}
