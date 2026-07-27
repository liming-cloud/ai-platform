package com.aiplatform.foundation.platform.web.error;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/**
 * 平台 Web 层能够独立识别的通用错误。
 */
enum CommonErrorCode implements ErrorCode {
    INVALID_REQUEST(
            "COMMON.REQUEST.0001",
            ErrorType.REQUEST,
            "请求格式不正确"),
    SYSTEM_FAILURE(
            "COMMON.SYSTEM.0001",
            ErrorType.SYSTEM,
            "系统暂时无法处理请求");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    CommonErrorCode(
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
        return this == SYSTEM_FAILURE;
    }

    @Override
    public Set<String> allowedDetailFields() {
        return Set.of();
    }
}
