package com.aiplatform.foundation.knowledgedata.application.knowledge;

/**
 * 同一幂等键被用于不同请求。
 */
public final class IdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException() {
        super("幂等键已经用于不同请求");
    }
}
