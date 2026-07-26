package com.aiplatform.foundation.knowledgedata.application.datasource;

/**
 * 当前租户和知识空间中不存在目标数据源。
 */
public final class DataSourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataSourceNotFoundException() {
        super("数据源不存在");
    }
}
