package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/** 配额保存的稳定脱敏错误，不公开知识数据或数据库内部状态。 */
public enum TenantQuotaErrorCode implements ErrorCode {
    VERSION_CONFLICT("ORGANIZATION_ACCESS.CONFLICT.0082", ErrorType.CONFLICT, "配额已被其他操作修改，请刷新后重试"),
    BELOW_USAGE("ORGANIZATION_ACCESS.CONFLICT.0083", ErrorType.CONFLICT, "配额不能低于当前已用量"),
    USAGE_INCOMPLETE("ORGANIZATION_ACCESS.CONFLICT.0084", ErrorType.CONFLICT, "当前用量尚未完整核实，暂不能下调资源上限");

    private final String code;
    private final ErrorType type;
    private final String message;

    TenantQuotaErrorCode(String code, ErrorType type, String message) {
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
