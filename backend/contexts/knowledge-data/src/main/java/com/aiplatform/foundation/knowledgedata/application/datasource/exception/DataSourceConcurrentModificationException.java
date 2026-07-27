package com.aiplatform.foundation.knowledgedata.application.datasource.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 数据源已被其他请求修改。
 */
public final class DataSourceConcurrentModificationException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public DataSourceConcurrentModificationException() {
        super(KnowledgeDataErrorCode.DATA_SOURCE_VERSION_CONFLICT);
    }
}
