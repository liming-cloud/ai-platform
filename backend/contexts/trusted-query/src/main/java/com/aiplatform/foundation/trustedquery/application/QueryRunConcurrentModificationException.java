package com.aiplatform.foundation.trustedquery.application;

/**
 * 查询运行终态被其他执行者抢先提交。
 */
public final class QueryRunConcurrentModificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueryRunConcurrentModificationException() {
        super("查询运行已被并发修改");
    }
}
