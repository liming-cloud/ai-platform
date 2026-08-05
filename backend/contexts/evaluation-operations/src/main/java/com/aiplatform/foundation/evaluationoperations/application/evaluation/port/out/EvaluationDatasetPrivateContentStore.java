package com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationContent;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/**
 * evaluation-operations 独占的私有数据集正文存储端口。
 */
public interface EvaluationDatasetPrivateContentStore {

    String put(TenantId tenantId, long datasetId, EvaluationDatasetRegistrationContent content);

    void delete(String privateContentReference);
}
