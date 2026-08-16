package com.aiplatform.foundation.evaluationoperations.application.operations.dto;

import java.util.Map;

/** 当前租户评测运行与质量门决定的脱敏计数。 */
public record EvaluationOperationsFacts(
        Map<String, Long> runsByStatus,
        Map<String, Long> releaseDecisionsByStatus) {

    public EvaluationOperationsFacts {
        runsByStatus = Map.copyOf(runsByStatus);
        releaseDecisionsByStatus = Map.copyOf(releaseDecisionsByStatus);
    }
}
