package com.aiplatform.foundation.evaluationoperations.domain.evaluation.operation;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;
import java.util.Objects;

/**
 * 评测领域服务返回的不可变终态操作结果。
 */
public record EvaluationCompletion(EvaluationRun run) {

    public EvaluationCompletion {
        run = Objects.requireNonNull(run, "完成后的评测运行不能为空");
    }
}
