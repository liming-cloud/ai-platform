package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.util.Objects;

/**
 * 可供发布决定消费的冻结运行最小事实，不包含评测正文或指标细节。
 */
public record EvaluationRunReleaseFact(
        TenantId tenantId,
        EvaluationRunId evaluationRunId,
        EvaluationTarget target,
        EvaluationRunStatus status) implements ValueObject {

    public EvaluationRunReleaseFact {
        tenantId = Objects.requireNonNull(tenantId, "评测运行租户不能为空");
        evaluationRunId = Objects.requireNonNull(evaluationRunId, "评测运行标识不能为空");
        target = Objects.requireNonNull(target, "评测运行目标不能为空");
        status = Objects.requireNonNull(status, "评测运行状态不能为空");
    }
}
