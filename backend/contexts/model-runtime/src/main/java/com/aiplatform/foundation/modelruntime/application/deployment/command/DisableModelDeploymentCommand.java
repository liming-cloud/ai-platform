package com.aiplatform.foundation.modelruntime.application.deployment.command;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.util.Objects;

/**
 * 停用模型部署；停用后的同一修订不能恢复发布。
 */
public record DisableModelDeploymentCommand(
        TrustedRequestContext context,
        String modelDeploymentId,
        long expectedVersion) {

    public DisableModelDeploymentCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(modelDeploymentId, "模型部署标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期版本不能为负数");
        }
    }
}
