package com.aiplatform.foundation.knowledgedata.application.datasource.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 同一知识空间内已经存在同名数据源。
 */
public final class DataSourceNameConflictException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public DataSourceNameConflictException() {
        super(KnowledgeDataErrorCode.DATA_SOURCE_NAME_CONFLICT);
    }
}
