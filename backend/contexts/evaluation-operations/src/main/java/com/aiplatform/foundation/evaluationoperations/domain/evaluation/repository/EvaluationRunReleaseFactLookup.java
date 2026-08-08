package com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunId;
import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRunReleaseFact;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Optional;

/** 供发布制度消费的同租户无正文评测运行事实读取边界。 */
public interface EvaluationRunReleaseFactLookup {
    Optional<EvaluationRunReleaseFact> findById(TenantId tenantId, EvaluationRunId id);
}
