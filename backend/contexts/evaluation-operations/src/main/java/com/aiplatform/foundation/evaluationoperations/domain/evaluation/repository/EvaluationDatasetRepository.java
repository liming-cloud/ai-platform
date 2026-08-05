package com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDataset;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationDatasetId;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/**
 * 评测数据集根的同租户加载契约；返回对象不包含私有正文。
 */
public interface EvaluationDatasetRepository {

    Optional<EvaluationDataset> findById(TenantId tenantId, EvaluationDatasetId id);
}
