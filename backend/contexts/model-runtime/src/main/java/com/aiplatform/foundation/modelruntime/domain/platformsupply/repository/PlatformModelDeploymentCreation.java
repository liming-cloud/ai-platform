package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import java.util.Objects;

/** 平台模型部署创建的幂等持久化输入。 */
public record PlatformModelDeploymentCreation(
        PlatformModelDeployment deployment,
        String idempotencyKey,
        String requestFingerprint) {
    public PlatformModelDeploymentCreation {
        deployment = Objects.requireNonNull(deployment, "平台模型部署不能为空");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "幂等键不能为空");
        requestFingerprint = Objects.requireNonNull(requestFingerprint, "请求指纹不能为空");
    }
}
