package com.aiplatform.foundation.modelruntime.api.catalog.model;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import java.time.Duration;
import java.util.Objects;

/**
 * 使用冻结供应商修订探测模型部署。
 */
public record ProbeModelDeploymentCommand(
        TrustedRequestContext context,
        String modelDeploymentId,
        long expectedVersion,
        Duration timeout) {

    public ProbeModelDeploymentCommand {
        Objects.requireNonNull(context, "可信请求上下文不能为空");
        Objects.requireNonNull(modelDeploymentId, "模型部署标识不能为空");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("预期版本不能为负数");
        }
        Objects.requireNonNull(timeout, "探测超时不能为空");
        if (timeout.isNegative()
                || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("探测超时必须在 0 到 30 秒之间");
        }
    }
}
