package com.aiplatform.foundation.organizationaccess.application.member.exception;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/** 账户冲突、归属拒绝和不可用错误均为稳定无秘密协议。 */
public enum MemberAccountErrorCode implements ErrorCode {
    CONFLICT("ORGANIZATION_ACCESS.CONFLICT.0093", ErrorType.CONFLICT, "账户或授权事实已变更，请刷新后重试"),
    DENIED("ORGANIZATION_ACCESS.AUTHORIZATION.0093", ErrorType.AUTHORIZATION, "当前账户操作不可用"),
    UNAVAILABLE("ORGANIZATION_ACCESS.SYSTEM.0093", ErrorType.SYSTEM, "账户操作暂时不可用");
    private final String code;
    private final ErrorType type;
    private final String message;
    MemberAccountErrorCode(String code, ErrorType type, String message) {
        this.code = code; this.type = type; this.message = message;
    }
    @Override public String code() { return code; }
    @Override public ErrorType type() { return type; }
    @Override public String defaultMessage() { return message; }
    @Override public boolean retryable() { return false; }
    @Override public Set<String> allowedDetailFields() { return Set.of(); }
}
