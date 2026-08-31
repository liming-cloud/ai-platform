package com.aiplatform.foundation.organizationaccess.domain.shared.exception;

import com.aiplatform.foundation.shared.kernel.error.DomainException;

/** 表示组织访问领域规则以稳定、安全的业务语义拒绝当前命令。 */
public final class OrganizationAccessRuleViolation extends DomainException {

    private static final long serialVersionUID = 1L;

    /** 使用不携带业务标识的稳定错误建立领域拒绝。 */
    public OrganizationAccessRuleViolation(OrganizationAccessRuleErrorCode errorCode) {
        super(errorCode);
    }
}
