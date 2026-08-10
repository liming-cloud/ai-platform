package com.aiplatform.foundation.modelruntime.application.deployment.port.out;

import com.aiplatform.foundation.modelruntime.domain.deployment.operation.ModelDeploymentVerificationResult;

/**
 * 原子提交模型能力验证事实及其对应部署修订的应用端口。
 */
@FunctionalInterface
public interface ModelDeploymentVerificationStore {

    /**
     * @throws IllegalStateException 当部署版本已改变或任一写入失败时拒绝。
     */
    void commit(ModelDeploymentVerificationResult result, long expectedDeploymentVersion);
}
