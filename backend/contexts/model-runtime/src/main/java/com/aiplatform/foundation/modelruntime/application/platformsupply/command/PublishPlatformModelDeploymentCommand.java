package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Objects;

/** 平台管理员发布已验证模型部署的输入。 */
public record PublishPlatformModelDeploymentCommand(
        PlatformRequestContext context, String modelDeploymentId, long expectedVersion) {
    public PublishPlatformModelDeploymentCommand {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        modelDeploymentId = Objects.requireNonNull(modelDeploymentId, "平台模型部署标识不能为空");
    }
}
