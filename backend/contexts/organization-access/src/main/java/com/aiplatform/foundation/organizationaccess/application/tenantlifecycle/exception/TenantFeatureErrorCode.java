package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/** 租户功能版本冲突不泄露当前设置或其他租户事实。 */
public enum TenantFeatureErrorCode implements ErrorCode {
    VERSION_CONFLICT("ORGANIZATION_ACCESS.CONFLICT.0085", ErrorType.CONFLICT,
            "功能配置已被其他操作修改，请刷新后重试"),
    FEATURE_DISABLED("ORGANIZATION_ACCESS.AUTHORIZATION.0085", ErrorType.AUTHORIZATION,
            "当前租户未开放此功能");

    private final String code;
    private final ErrorType type;
    private final String message;

    TenantFeatureErrorCode(String code, ErrorType type, String message) {
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
