package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/** 策略并发冲突不暴露其他租户或失败计数事实。 */
public enum TenantLoginSecurityErrorCode implements ErrorCode {
    VERSION_CONFLICT("ORGANIZATION_ACCESS.CONFLICT.0086", ErrorType.CONFLICT,
            "登录策略已被其他操作修改，请刷新后重试");

    private final String code;
    private final ErrorType type;
    private final String message;

    TenantLoginSecurityErrorCode(String code, ErrorType type, String message) {
        this.code = code;
        this.type = type;
        this.message = message;
    }

    @Override public String code() { return code; }
    @Override public ErrorType type() { return type; }
    @Override public String defaultMessage() { return message; }
    @Override public boolean retryable() { return false; }
    @Override public Set<String> allowedDetailFields() { return Set.of(); }
}
