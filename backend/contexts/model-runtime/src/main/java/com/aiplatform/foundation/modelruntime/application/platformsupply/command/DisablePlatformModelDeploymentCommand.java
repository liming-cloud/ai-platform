package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.util.Objects;

/** 停用平台模型部署；停用后同一修订不得恢复发布。 */
public record DisablePlatformModelDeploymentCommand(
        PlatformRequestContext context,
        String modelDeploymentId,
        long expectedVersion,
        java.util.function.BooleanSupplier sessionActive) {
    public DisablePlatformModelDeploymentCommand(PlatformRequestContext context, String id, long version) {
        this(context, id, version, () -> false);
    }
    public DisablePlatformModelDeploymentCommand {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        modelDeploymentId = Objects.requireNonNull(modelDeploymentId, "平台模型部署标识不能为空");
        Objects.requireNonNull(sessionActive, "必须提供当前服务端会话连续性");
        if (expectedVersion < 0) throw new IllegalArgumentException("预期版本不能为负数");
    }
}
