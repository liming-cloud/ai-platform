package com.aiplatform.foundation.evaluationoperations.domain.evaluation.repository;

import com.aiplatform.foundation.evaluationoperations.domain.evaluation.model.EvaluationRun;

/**
 * 已完成评测运行根的提交契约；实现不得写入私有评测正文。
 */
public interface EvaluationRunRepository {

    void save(EvaluationRun run);
}
