package com.aiplatform.foundation.trustedquery.application.run;

/**
 * 查询运行在当前租户中不存在。
 */
public final class QueryRunNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueryRunNotFoundException() {
        super("查询运行不存在");
    }
}
