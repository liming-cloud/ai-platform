package com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;

/**
 * 原子提交已发布数据集清单的应用持久化端口。
 */
public interface EvaluationDatasetRegistrationStore {

    void commit(EvaluationDataset dataset);
}
