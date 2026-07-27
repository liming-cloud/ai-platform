package com.aiplatform.foundation.knowledgedata.application.datasource.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 租户数据源数量已经达到限制。
 */
public final class DataSourceQuotaExceededException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public DataSourceQuotaExceededException() {
        super(KnowledgeDataErrorCode.DATA_SOURCE_QUOTA_EXCEEDED);
    }
}
