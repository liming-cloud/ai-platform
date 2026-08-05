package com.aiplatform.foundation.evaluationoperations.application.evaluation.request;

/**
 * 触发评测运行的无正文请求；租户和主体始终来自可信请求上下文。
 */
public record EvaluationRunExecutionRequest(long datasetId, long assistantVersionId) {

    public EvaluationRunExecutionRequest {
        if (datasetId <= 0L || assistantVersionId <= 0L) {
            throw new IllegalArgumentException("评测数据集或助手版本标识不正确");
        }
    }
}
