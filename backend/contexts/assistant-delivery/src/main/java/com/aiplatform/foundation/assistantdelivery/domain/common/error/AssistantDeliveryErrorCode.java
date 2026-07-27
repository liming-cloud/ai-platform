package com.aiplatform.foundation.assistantdelivery.domain.common.error;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/**
 * 助手交付上下文的稳定错误编码。
 */
public enum AssistantDeliveryErrorCode implements ErrorCode {
    ACCESS_DENIED("ASSISTANT_DELIVERY.AUTHORIZATION.0001", ErrorType.AUTHORIZATION,
            "没有执行该助手操作的权限"),
    ASSISTANT_NOT_FOUND("ASSISTANT_DELIVERY.RESOURCE.0001", ErrorType.RESOURCE,
            "助手不存在"),
    VERSION_NOT_FOUND("ASSISTANT_DELIVERY.RESOURCE.0002", ErrorType.RESOURCE,
            "助手版本不存在"),
    CONCURRENT("ASSISTANT_DELIVERY.CONFLICT.0001", ErrorType.CONFLICT,
            "助手数据已经发生变化"),
    IDEMPOTENCY("ASSISTANT_DELIVERY.CONFLICT.0002", ErrorType.CONFLICT,
            "幂等键已经用于不同的助手请求"),
    NAME("ASSISTANT_DELIVERY.CONFLICT.0003", ErrorType.CONFLICT,
            "当前租户已经存在同名助手"),
    VERSION_STATE("ASSISTANT_DELIVERY.CONFLICT.0004", ErrorType.CONFLICT,
            "助手版本当前状态不允许该操作"),
    ASSISTANT_QUOTA("ASSISTANT_DELIVERY.QUOTA.0001", ErrorType.QUOTA,
            "租户助手数量已经达到上限"),
    VERSION_QUOTA("ASSISTANT_DELIVERY.QUOTA.0002", ErrorType.QUOTA,
            "助手版本数量已经达到上限"),
    REFERENCE_UNAVAILABLE(
            "ASSISTANT_DELIVERY.DEPENDENCY_UNAVAILABLE.0001",
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "助手版本引用的模型或知识索引当前不可用");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    AssistantDeliveryErrorCode(
            String code,
            ErrorType type,
            String defaultMessage) {
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
