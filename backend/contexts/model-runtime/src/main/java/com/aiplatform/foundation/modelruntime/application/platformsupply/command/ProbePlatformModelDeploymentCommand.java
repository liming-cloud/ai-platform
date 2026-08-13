package com.aiplatform.foundation.modelruntime.application.platformsupply.command;

import com.aiplatform.foundation.security.identity.PlatformRequestContext;
import java.time.Duration;
import java.util.Objects;

/** 平台管理员对草稿部署执行能力探测的输入。 */
public record ProbePlatformModelDeploymentCommand(
        PlatformRequestContext context, String modelDeploymentId, long expectedVersion, Duration timeout) {
    public ProbePlatformModelDeploymentCommand {
        context = Objects.requireNonNull(context, "平台请求上下文不能为空");
        modelDeploymentId = Objects.requireNonNull(modelDeploymentId, "平台模型部署标识不能为空");
        timeout = Objects.requireNonNull(timeout, "探测超时不能为空");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("探测超时必须大于零");
    }
}
