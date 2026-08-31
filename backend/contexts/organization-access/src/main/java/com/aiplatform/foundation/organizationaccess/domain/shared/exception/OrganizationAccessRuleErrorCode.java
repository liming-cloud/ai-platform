package com.aiplatform.foundation.organizationaccess.domain.shared.exception;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/** 组织访问领域规则可安全公开的稳定错误编码。 */
public enum OrganizationAccessRuleErrorCode implements ErrorCode {
    PERSONNEL_SCOPE_CONFLICT(
            "ORGANIZATION_ACCESS.CONFLICT.0002",
            ErrorType.CONFLICT,
            "该账户存在冲突的人员身份，请先停用另一范围身份后重试"),
    LAST_ENABLED_TENANT_ADMINISTRATOR(
            "ORGANIZATION_ACCESS.CONFLICT.0003",
            ErrorType.CONFLICT,
            "不能停用租户最后一个启用管理员"),
    TENANT_ADMINISTRATOR_PLATFORM_OWNED(
            "ORGANIZATION_ACCESS.AUTHORIZATION.0002",
            ErrorType.AUTHORIZATION,
            "租户管理员只能治理本租户的普通成员");

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;

    OrganizationAccessRuleErrorCode(String code, ErrorType type, String defaultMessage) {
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
