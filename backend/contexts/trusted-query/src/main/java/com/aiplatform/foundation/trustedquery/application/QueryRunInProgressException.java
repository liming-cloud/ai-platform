package com.aiplatform.foundation.trustedquery.application;

/**
 * 相同幂等请求仍在处理中。
 */
public final class QueryRunInProgressException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueryRunInProgressException() {
        super("相同查询仍在处理中");
    }
}
