package com.aiplatform.foundation.knowledgedata.application.datasource;

/**
 * 数据源已被其他请求修改。
 */
public final class DataSourceConcurrentModificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataSourceConcurrentModificationException() {
        super("数据源版本已经变化");
    }
}
