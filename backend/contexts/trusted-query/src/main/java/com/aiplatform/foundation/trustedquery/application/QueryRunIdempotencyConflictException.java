package com.aiplatform.foundation.trustedquery.application;

/**
 * 相同幂等键被用于不同查询请求。
 */
public final class QueryRunIdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueryRunIdempotencyConflictException() {
        super("幂等键已用于其他查询");
    }
}
