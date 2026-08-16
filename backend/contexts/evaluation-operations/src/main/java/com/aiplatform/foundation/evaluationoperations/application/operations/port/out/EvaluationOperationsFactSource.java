package com.aiplatform.foundation.evaluationoperations.application.operations.port.out;

import com.aiplatform.foundation.evaluationoperations.application.operations.dto.EvaluationOperationsFacts;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;

/** 读取评测上下文拥有的运行与质量门脱敏计数。 */
public interface EvaluationOperationsFactSource {

    EvaluationOperationsFacts tenantSummary(TenantId tenantId);
}
