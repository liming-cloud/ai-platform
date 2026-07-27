package com.aiplatform.foundation.knowledgedata.application.indexversion.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 可信租户范围内不存在目标索引版本。
 */
public final class IndexVersionNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public IndexVersionNotFoundException() {
        super(KnowledgeDataErrorCode.INDEX_VERSION_NOT_FOUND);
    }
}
