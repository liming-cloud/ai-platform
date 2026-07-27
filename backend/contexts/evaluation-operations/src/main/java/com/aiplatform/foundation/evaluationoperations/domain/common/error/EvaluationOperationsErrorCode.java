package com.aiplatform.foundation.evaluationoperations.domain.common.error;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/**
 * 评测运维上下文的稳定错误编码。
 */
public enum EvaluationOperationsErrorCode implements ErrorCode {
    ACCESS_DENIED(
            "EVALUATION_OPERATIONS.AUTHORIZATION.0001",
            ErrorType.AUTHORIZATION,
            "没有读取运营事实的权限");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    EvaluationOperationsErrorCode(
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
        return false;
    }

    @Override
    public Set<String> allowedDetailFields() {
        return Set.of();
    }
}
