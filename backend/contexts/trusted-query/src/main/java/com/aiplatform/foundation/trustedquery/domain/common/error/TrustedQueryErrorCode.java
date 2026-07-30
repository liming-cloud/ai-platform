package com.aiplatform.foundation.trustedquery.domain.common.error;

import com.aiplatform.foundation.shared.kernel.error.ErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ErrorType;
import java.util.Set;

/**
 * 可信查询上下文的稳定错误编码。
 */
public enum TrustedQueryErrorCode implements ErrorCode {
    ACCESS_DENIED("TRUSTED_QUERY.AUTHORIZATION.0001", ErrorType.AUTHORIZATION,
            "没有执行该查询操作的权限", Set.of()),
    RUN_NOT_FOUND("TRUSTED_QUERY.RESOURCE.0001", ErrorType.RESOURCE,
            "查询运行不存在", Set.of()),
    TARGET_NOT_FOUND("TRUSTED_QUERY.RESOURCE.0002", ErrorType.RESOURCE,
            "请求的资源不存在", Set.of()),
    ASSISTANT_UNAVAILABLE(
            "TRUSTED_QUERY.DEPENDENCY_UNAVAILABLE.0001",
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "已发布助手版本不可用",
            Set.of()),
    INDEX_UNAVAILABLE(
            "TRUSTED_QUERY.DEPENDENCY_UNAVAILABLE.0002",
            ErrorType.DEPENDENCY_UNAVAILABLE,
            "知识空间没有可用的活动索引",
            Set.of()),
    IDEMPOTENCY("TRUSTED_QUERY.CONFLICT.0001", ErrorType.CONFLICT,
            "幂等键已经用于不同查询", Set.of()),
    CONCURRENT("TRUSTED_QUERY.CONFLICT.0002", ErrorType.CONFLICT,
            "查询运行已经发生变化", Set.of()),
    IN_PROGRESS("TRUSTED_QUERY.CONFLICT.0003", ErrorType.CONFLICT,
            "相同查询仍在处理中", Set.of()),
    FEEDBACK_EXISTS("TRUSTED_QUERY.CONFLICT.0004", ErrorType.CONFLICT,
            "当前成员已经提交过该查询反馈", Set.of()),
    QUOTA("TRUSTED_QUERY.QUOTA.0001", ErrorType.QUOTA,
            "查询配额已经用尽", Set.of("limitType", "limit", "retryAt"));

    private final String code;
    private final ErrorType type;
    private final String defaultMessage;
    private final Set<String> allowedDetails;

    TrustedQueryErrorCode(
            String code,
            ErrorType type,
            String defaultMessage,
            Set<String> allowedDetails) {
        this.code = code;
        this.type = type;
        this.defaultMessage = defaultMessage;
        this.allowedDetails = allowedDetails;
    }

    @Override
    public String code() { return code; }

    @Override
    public ErrorType type() { return type; }

    @Override
    public String defaultMessage() { return defaultMessage; }

    @Override
    public boolean retryable() {
        return type == ErrorType.DEPENDENCY_UNAVAILABLE || type == ErrorType.QUOTA;
    }

    @Override
    public Set<String> allowedDetailFields() { return allowedDetails; }
}
