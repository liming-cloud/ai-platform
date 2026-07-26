package com.aiplatform.foundation.modelruntime.application.port.out;

import com.aiplatform.foundation.modelruntime.domain.model.ModelDeployment;
import java.util.Objects;

/**
 * 携带幂等事实的模型部署创建请求。
 */
public record ModelDeploymentCreation(
        ModelDeployment deployment,
        String idempotencyKey,
        String requestFingerprint) {

    public ModelDeploymentCreation {
        Objects.requireNonNull(deployment, "模型部署不能为空");
        Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
    }
}
