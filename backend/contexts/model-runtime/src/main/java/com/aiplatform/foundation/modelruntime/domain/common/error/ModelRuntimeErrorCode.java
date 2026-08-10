package com.aiplatform.foundation.modelruntime.domain.common.error;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/**
 * 模型运行时上下文的稳定错误编码。
 */
public enum ModelRuntimeErrorCode implements ErrorCode {
    ACCESS_DENIED("MODEL_RUNTIME.AUTHORIZATION.0001", ErrorType.AUTHORIZATION,
            "没有执行该模型治理操作的权限"),
    PROVIDER_NOT_FOUND("MODEL_RUNTIME.RESOURCE.0001", ErrorType.RESOURCE,
            "供应商连接不存在"),
    DEPLOYMENT_NOT_FOUND("MODEL_RUNTIME.RESOURCE.0002", ErrorType.RESOURCE,
            "模型部署不存在"),
    MODEL_CONFIGURATION_NOT_FOUND("MODEL_RUNTIME.RESOURCE.0003", ErrorType.RESOURCE,
            "模型配置不存在"),
    PROVIDER_CONCURRENT("MODEL_RUNTIME.CONFLICT.0001", ErrorType.CONFLICT,
            "供应商连接版本已经变化"),
    PROVIDER_IDEMPOTENCY("MODEL_RUNTIME.CONFLICT.0002", ErrorType.CONFLICT,
            "幂等键已经用于不同供应商连接请求"),
    PROVIDER_NAME("MODEL_RUNTIME.CONFLICT.0003", ErrorType.CONFLICT,
            "当前租户已经存在同名供应商连接"),
    DEPLOYMENT_CONCURRENT("MODEL_RUNTIME.CONFLICT.0004", ErrorType.CONFLICT,
            "模型部署版本已经变化"),
    DEPLOYMENT_IDEMPOTENCY("MODEL_RUNTIME.CONFLICT.0005", ErrorType.CONFLICT,
            "模型部署幂等键已经用于不同请求"),
    DEPLOYMENT_NAME("MODEL_RUNTIME.CONFLICT.0006", ErrorType.CONFLICT,
            "当前租户已经存在同名模型部署"),
    PROVIDER_STATE("MODEL_RUNTIME.CONFLICT.0007", ErrorType.CONFLICT,
            "供应商连接当前状态不允许该操作"),
    DEPLOYMENT_STATE("MODEL_RUNTIME.CONFLICT.0008", ErrorType.CONFLICT,
            "模型部署当前状态不允许该操作"),
    PROVIDER_QUOTA("MODEL_RUNTIME.QUOTA.0001", ErrorType.QUOTA,
            "供应商连接数量已经达到租户配额"),
    DEPLOYMENT_QUOTA("MODEL_RUNTIME.QUOTA.0002", ErrorType.QUOTA,
            "模型部署数量已经达到租户配额"),
    PROVIDER_UNAVAILABLE(
            "MODEL_RUNTIME.DEPENDENCY_UNAVAILABLE.0001",
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "模型部署冻结的供应商连接不可用"),
    RUNTIME_UNAVAILABLE(
            "MODEL_RUNTIME.DEPENDENCY_UNAVAILABLE.0002",
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "模型运行时当前不可用"),
    DEPLOYMENT_DISCOVERY_UNAVAILABLE(
            "MODEL_RUNTIME.CONFLICT.0009",
            ErrorType.CONFLICT,
            "当前供应商连接修订不存在有效模型发现候选"),
    UNSUPPORTED_PROVIDER("MODEL_RUNTIME.REQUEST.0001", ErrorType.REQUEST,
            "当前运行进程未装配请求的供应商协议");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    ModelRuntimeErrorCode(String code, ErrorType type, String defaultMessage) {
        this.code = code;
        this.type = type;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public boolean retryable() {
        return type == ErrorType.DEPENDENCY_UNAVAILABLE;
    }

    @Override
    public Set<String> allowedDetailFields() {
        return Set.of();
    }
}
