package com.aiplatform.foundation.knowledgedata.application.datasource;

/**
 * 同一知识空间内已经存在同名数据源。
 */
public final class DataSourceNameConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataSourceNameConflictException() {
        super("当前知识空间已经存在同名数据源");
    }
}
