package com.aiplatform.foundation.knowledgedata.application.datasource.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 当前租户和知识空间中不存在目标数据源。
 */
public final class DataSourceNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public DataSourceNotFoundException() {
        super(KnowledgeDataErrorCode.DATA_SOURCE_NOT_FOUND);
    }
}
