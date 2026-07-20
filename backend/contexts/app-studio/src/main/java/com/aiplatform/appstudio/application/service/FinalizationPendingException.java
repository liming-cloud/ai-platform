package com.aiplatform.appstudio.application.service;

import java.util.Objects;

/** RESULT_RECEIVED 已持久化后的内部控制流异常，保留只重放最终事务的恢复语义。 */
final class FinalizationPendingException extends RuntimeException {
    private final RuntimeException cause;

    FinalizationPendingException(RuntimeException cause) {
        super(null, cause, false, false);
        this.cause = Objects.requireNonNull(cause, "完成事实持久化后异常不能为空");
    }

    RuntimeException cause() {
        return cause;
    }
}
