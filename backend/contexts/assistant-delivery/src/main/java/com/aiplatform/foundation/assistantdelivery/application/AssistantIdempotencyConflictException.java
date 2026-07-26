package com.aiplatform.foundation.assistantdelivery.application;

/**
 * 同一幂等键被用于不同助手请求。
 */
public final class AssistantIdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AssistantIdempotencyConflictException() {
        super("幂等键已用于不同的助手请求");
    }
}
