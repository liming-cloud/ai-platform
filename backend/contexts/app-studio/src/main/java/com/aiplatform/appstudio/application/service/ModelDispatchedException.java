package com.aiplatform.appstudio.application.service;

import java.util.Objects;

/** 模型请求已派发后的内部控制流异常，防止把运行错误误判为可安全重试的 PREPARING 失败。 */
final class ModelDispatchedException extends RuntimeException {
    private final RuntimeException cause;

    ModelDispatchedException(RuntimeException cause) {
        super(null, cause, false, false);
        this.cause = Objects.requireNonNull(cause, "模型派发后异常不能为空");
    }

    RuntimeException cause() {
        return cause;
    }
}
