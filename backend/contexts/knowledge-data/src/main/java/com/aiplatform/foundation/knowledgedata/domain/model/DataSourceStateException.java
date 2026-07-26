package com.aiplatform.foundation.knowledgedata.domain.model;

/**
 * 数据源当前状态不允许目标操作。
 */
public final class DataSourceStateException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public DataSourceStateException(String message) {
        super(message);
    }
}
