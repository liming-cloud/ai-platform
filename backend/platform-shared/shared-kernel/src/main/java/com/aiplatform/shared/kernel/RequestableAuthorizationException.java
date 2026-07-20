package com.aiplatform.shared.kernel;

import java.util.Objects;

/**
 * 表示当前拒绝仅缺少一个可通过资源授权申请补齐的目标；RBAC、状态和策略拒绝不得使用该异常。
 */
public final class RequestableAuthorizationException extends ApplicationException {
    private final AuthorizationRequestTarget target;

    public RequestableAuthorizationException(
            ErrorCode errorCode,
            String detail,
            AuthorizationRequestTarget target
    ) {
        super(errorCode, detail);
        this.target = Objects.requireNonNull(target, "可申请授权目标不能为空");
    }

    public AuthorizationRequestTarget target() {
        return target;
    }
}
