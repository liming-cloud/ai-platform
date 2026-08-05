package com.aiplatform.foundation.evaluationoperations.application.evaluation.port.out;

import com.aiplatform.foundation.evaluationoperations.application.evaluation.request.EvaluationDatasetRegistrationContent;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.io.InputStream;

/**
 * evaluation-operations 独占的私有数据集正文存储端口。
 */
public interface EvaluationDatasetPrivateContentStore {

    String put(TenantId tenantId, long datasetId, EvaluationDatasetRegistrationContent content);

    /**
     * 打开仅供受控评测运行逐条消费的私有内容流；调用方不得投影或保留内容。
     */
    InputStream open(String privateContentReference);

    void delete(String privateContentReference);
}
