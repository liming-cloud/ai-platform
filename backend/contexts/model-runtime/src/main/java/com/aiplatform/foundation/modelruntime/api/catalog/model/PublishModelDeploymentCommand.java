package com.aiplatform.foundation.modelruntime.api.catalog.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 发布已经验证的模型部署。
 */
public record PublishModelDeploymentCommand(
        TrustedRequestContext context,
        String modelDeploymentId,
        long expectedVersion) {

    public PublishModelDeploymentCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(modelDeploymentId, "模型部署标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期版本不能为负数");
        }
    }
}
