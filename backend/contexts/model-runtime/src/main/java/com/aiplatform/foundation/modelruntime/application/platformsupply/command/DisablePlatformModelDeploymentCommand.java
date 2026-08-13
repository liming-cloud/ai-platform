package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Objects;

/** 停用平台模型部署；停用后同一修订不得恢复发布。 */
public record DisablePlatformModelDeploymentCommand(
        PlatformRequestContext context,
        String modelDeploymentId,
        long expectedVersion) {
    public DisablePlatformModelDeploymentCommand {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        modelDeploymentId = Objects.requireNonNull(modelDeploymentId, "平台模型部署标识不能为空");
        if (expectedVersion < 0) throw new IllegalArgumentException("预期版本不能为负数");
    }
}
