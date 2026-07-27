package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.DomainException;

/**
 * 数据源当前状态不允许目标操作。
 */
public final class DataSourceStateException extends DomainException {

    private static final long serialVersionUID = 1L;

    public DataSourceStateException(String message) {
        super(KnowledgeDataErrorCode.DATA_SOURCE_STATE_CONFLICT);
    }
}
