package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Objects;

/** 平台管理员发布已验证模型部署的输入。 */
public record PublishPlatformModelDeploymentCommand(
        PlatformRequestContext context, String modelDeploymentId, long expectedVersion,
        java.util.function.BooleanSupplier sessionActive) {
    public PublishPlatformModelDeploymentCommand(PlatformRequestContext context, String id, long version) {
        this(context, id, version, () -> false);
    }
    public PublishPlatformModelDeploymentCommand {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        modelDeploymentId = Objects.requireNonNull(modelDeploymentId, "平台模型部署标识不能为空");
        Objects.requireNonNull(sessionActive, "必须提供当前服务端会话连续性");
        if (expectedVersion < 0) throw new IllegalArgumentException("部署修订无效");
    }
}
