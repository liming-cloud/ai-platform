package com.aiplatform.foundation.knowledgedata.application.indexversion.exception;

import com.aiplatform.foundation.knowledgedata.domain.common.error.KnowledgeDataErrorCode;
import com.aiplatform.foundation.shared.kernel.error.ApplicationException;

/**
 * 索引版本状态或乐观锁修订已经改变。
 */
public final class IndexVersionConcurrentModificationException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public IndexVersionConcurrentModificationException() {
        super(KnowledgeDataErrorCode.INDEX_VERSION_CONFLICT);
    }
}
