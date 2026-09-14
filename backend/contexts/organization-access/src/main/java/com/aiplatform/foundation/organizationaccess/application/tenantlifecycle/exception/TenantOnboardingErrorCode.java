package com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.exception;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/** 开户与只读复核的稳定脱敏错误；不得携带数据库错误或凭据详情。 */
public enum TenantOnboardingErrorCode implements ErrorCode {
    IDENTITY_CONFLICT("ORGANIZATION_ACCESS.CONFLICT.0080", ErrorType.CONFLICT, "管理员登录名已被使用"),
    TENANT_CONFLICT("ORGANIZATION_ACCESS.CONFLICT.0081", ErrorType.CONFLICT, "租户编码已被使用"),
    AUTHENTICATION_NOT_READY("ORGANIZATION_ACCESS.DEPENDENCY_UNAVAILABLE.0001", ErrorType.DEPENDENCY_UNAVAILABLE,
            "租户认证环境尚未就绪，请联系运维完成初始化"),
    ACCESS_DENIED("ORGANIZATION_ACCESS.AUTHORIZATION.0080", ErrorType.AUTHORIZATION, "没有租户治理权限"),
    TENANT_NOT_FOUND("ORGANIZATION_ACCESS.RESOURCE.0080", ErrorType.RESOURCE, "租户不存在"),
    IDENTITY_WRITE_FAILED("ORGANIZATION_ACCESS.SYSTEM.0080", ErrorType.SYSTEM, "管理员身份保存失败，请稍后重试");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    TenantOnboardingErrorCode(String code, ErrorType type, String defaultMessage) {
        this.code = code;
        this.type = type;
        this.defaultMessage = defaultMessage;
    }

    @Override public String code() { return code; }
    @Override public ErrorType type() { return type; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public boolean retryable() { return this == AUTHENTICATION_NOT_READY; }
    @Override public Set<String> allowedDetailFields() { return Set.of(); }
}
