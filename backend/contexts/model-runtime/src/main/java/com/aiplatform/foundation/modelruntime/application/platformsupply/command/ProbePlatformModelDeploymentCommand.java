package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** 平台管理员对草稿部署执行能力探测的输入。 */
public record ProbePlatformModelDeploymentCommand(
        PlatformRequestContext context, String modelDeploymentId, long expectedVersion, Duration timeout,
        BooleanSupplier sessionActive) {
    public ProbePlatformModelDeploymentCommand {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        modelDeploymentId = Objects.requireNonNull(modelDeploymentId, "平台模型部署标识不能为空");
        timeout = Objects.requireNonNull(timeout, "探测超时不能为空");
        if (expectedVersion < 0 || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("探测修订或期限不正确");
        Objects.requireNonNull(sessionActive, "必须提供服务端平台会话复核");
    }
}
