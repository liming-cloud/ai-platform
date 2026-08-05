package com.aiplatform.foundation.evaluationoperations.interfaces.web.evaluation.controller;

/**
 * 触发评测运行的受限 HTTP 请求；租户、主体和权限只从服务端会话恢复。
 */
public record EvaluationRunExecuteRequest(long datasetId, long assistantVersionId) {

    public EvaluationRunExecuteRequest {
        if (datasetId <= 0L || assistantVersionId <= 0L) {
            throw new IllegalArgumentException("评测数据集或助手版本标识不正确");
        }
    }
}
