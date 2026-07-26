package com.aiplatform.foundation.knowledgedata.application.datasource;

/**
 * 租户数据源数量已经达到限制。
 */
public final class DataSourceQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataSourceQuotaExceededException() {
        super("数据源数量已经达到租户配额");
    }
}
